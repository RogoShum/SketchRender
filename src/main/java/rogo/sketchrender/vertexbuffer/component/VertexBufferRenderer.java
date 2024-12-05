package rogo.sketchrender.vertexbuffer.component;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.vertexbuffer.BufferBuilder;
import rogo.sketchrender.vertexbuffer.DrawMode;
import rogo.sketchrender.vertexbuffer.attribute.GLVertex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public abstract class VertexBufferRenderer implements AutoCloseable {
    protected final VertexAttribute staticBuffer;
    protected final VertexAttribute dynamicBuffer;
    private int arrayObjectId;
    protected int indexCount;
    protected int instanceCount;
    protected final VertexFormat.Mode mode;
    protected final DrawMode drawMode;

    public VertexBufferRenderer(VertexFormat.Mode mode, DrawMode drawMode, GLVertex[] mainVertices, Consumer<BufferBuilder<?>> bufferConsumer, GLVertex[] mutableVertices) {
        this.staticBuffer = staticAttributeSuppler().apply(mainVertices);
        this.dynamicBuffer = mutableAttributeSuppler().apply(mutableVertices);
        this.arrayObjectId = GL30.glGenVertexArrays();
        this.mode = mode;
        this.drawMode = drawMode;
        init(bufferConsumer);
    }

    protected Function<GLVertex[], StaticAttribute> staticAttributeSuppler() {
        return StaticAttribute::new;
    }

    protected Function<GLVertex[], DynamicAttribute> mutableAttributeSuppler() {
        return DynamicAttribute::new;
    }

    public void init(Consumer<BufferBuilder<?>> bufferConsumer) {
        bindVertexArray();
        staticBuffer.bindVertexAttribArray();
        staticBuffer.init(bufferConsumer);
        dynamicBuffer.bindVertexAttribArray();
        unbindVertexArray();
        unbindVBO();
        this.indexCount = mode.indexCount(staticBuffer.vertexCount());
    }

    public void addInstanceAttrib(Consumer<ByteBuffer> consumer) {
        dynamicBuffer.addAttrib(consumer);
        instanceCount++;
    }

    public void unbindVBO() {
        GlStateManager._glBindBuffer(34962, 0);
    }

    private void bindVertexArray() {
        GL30.glBindVertexArray(this.arrayObjectId);
    }

    public static void unbindVertexArray() {
        GL30.glBindVertexArray(0);
    }

    public void drawWithShader(ShaderInstance shader, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        if (this.indexCount <= 0) {
            SketchRender.LOGGER.warn("SketchRender VertexBuffer indexCount can't be {}!", this.indexCount);
            return;
        }
        if (this.instanceCount > 0 || drawMode == DrawMode.NORMAL) {
            RenderSystem.assertOnRenderThread();
            BufferUploader.reset();

            for (int i = 0; i < 12; ++i) {
                int j = RenderSystem.getShaderTexture(i);
                shader.setSampler("Sampler" + i, j);
            }

            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
            }

            if (shader.PROJECTION_MATRIX != null) {
                shader.PROJECTION_MATRIX.set(projectionMatrix);
            }

            if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) {
                shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
            }

            if (shader.COLOR_MODULATOR != null) {
                shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
            }

            if (shader.FOG_START != null) {
                shader.FOG_START.set(RenderSystem.getShaderFogStart());
            }

            if (shader.FOG_END != null) {
                shader.FOG_END.set(RenderSystem.getShaderFogEnd());
            }

            if (shader.FOG_COLOR != null) {
                shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
            }

            if (shader.FOG_SHAPE != null) {
                shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
            }

            if (shader.TEXTURE_MATRIX != null) {
                shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
            }

            if (shader.GAME_TIME != null) {
                shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
            }

            if (shader.SCREEN_SIZE != null) {
                Window window = Minecraft.getInstance().getWindow();
                shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
            }

            if (shader.LINE_WIDTH != null && (this.mode == VertexFormat.Mode.LINES || this.mode == VertexFormat.Mode.LINE_STRIP)) {
                shader.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
            }

            RenderSystem.setupShaderLights(shader);

            dynamicBuffer.updateVertexAttrib();
            unbindVBO();
            bindVertexArray();
            shader.apply();

            if (drawMode == DrawMode.INSTANCED) {
                GL31.glDrawArraysInstanced(this.mode.asGLMode, 0, this.indexCount, this.instanceCount);
            } else {
                GL31.glDrawArrays(this.mode.asGLMode, 0, this.indexCount);
            }

            shader.clear();
            unbindVertexArray();
            this.instanceCount = 0;
        }
    }

    public void close() {
        staticBuffer.close();
        dynamicBuffer.close();

        if (this.arrayObjectId > 0) {
            RenderSystem.glDeleteVertexArrays(this.arrayObjectId);
            this.arrayObjectId = 0;
        }
    }
}