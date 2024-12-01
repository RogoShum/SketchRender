package rogo.sketchrender.vertexbuffer.component;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketchrender.api.CullingRenderEvent;
import rogo.sketchrender.vertexbuffer.DrawMode;
import rogo.sketchrender.vertexbuffer.attribute.GLVertex;

import java.nio.FloatBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.lwjgl.opengl.GL15.glBindBuffer;

@OnlyIn(Dist.CLIENT)
public abstract class VertexBufferRenderer implements AutoCloseable {
    protected final VertexAttribute main;
    protected final VertexAttribute mutable;
    private int arrayObjectId;
    protected int indexCount;
    protected int instanceCount;
    protected final VertexFormat.Mode mode;
    protected final DrawMode drawMode;

    public VertexBufferRenderer(VertexFormat.Mode mode, DrawMode drawMode, GLVertex[] mainVertices, Consumer<FloatBuffer> bufferConsumer, GLVertex[] mutableVertices) {
        this.main = mainAttributeSuppler().apply(mainVertices);
        this.mutable = mutableAttributeSuppler().apply(mutableVertices);
        this.arrayObjectId = GL30.glGenVertexArrays();
        this.mode = mode;
        this.drawMode = drawMode;
        init(bufferConsumer);
    }

    protected Function<GLVertex[], MainAttribute> mainAttributeSuppler() {
        return MainAttribute::new;
    }

    protected Function<GLVertex[], MutableAttribute> mutableAttributeSuppler() {
        return MutableAttribute::new;
    }

    public void init(Consumer<FloatBuffer> bufferConsumer) {
        bindVertexArray();
        main.bindVertexAttribArray();
        main.init(bufferConsumer);
        mutable.bindVertexAttribArray();
        unbindVertexArray();
        unbindVBO();
        this.indexCount = mode.indexCount(main.vertexCount());
    }

    public void addInstanceAttrib(Consumer<FloatBuffer> consumer) {
        mutable.addAttrib(consumer);
        instanceCount++;
    }

    public void unbindVBO() {
        glBindBuffer(34962, 0);
    }

    private void bindVertexArray() {
        GL30.glBindVertexArray(this.arrayObjectId);
    }

    public static void unbindVertexArray() {
        GL30.glBindVertexArray(0);
    }

    public void drawWithShader(ShaderInstance shader) {
        if ((this.indexCount != 0 && this.instanceCount > 0) || drawMode == DrawMode.NORMAL) {
            RenderSystem.assertOnRenderThread();
            BufferUploader.reset();

            for (int i = 0; i < 12; ++i) {
                int j = RenderSystem.getShaderTexture(i);
                shader.setSampler("Sampler" + i, j);
            }

            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
            }

            if (shader.PROJECTION_MATRIX != null) {
                shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
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

            CullingRenderEvent.setUniform(shader);
            RenderSystem.setupShaderLights(shader);

            mutable.updateVertexAttrib();
            unbindVBO();
            bindVertexArray();
            shader.apply();

            if (drawMode == DrawMode.INSTANCED) {
                GL31.glDrawArraysInstanced(this.mode.asGLMode, 0, this.indexCount, this.instanceCount);
            } else {
                GL31.glDrawElements(this.mode.asGLMode, 0, this.indexCount, this.instanceCount);
            }

            shader.clear();
            unbindVertexArray();
            this.instanceCount = 0;
        }
    }

    public void close() {
        main.close();
        mutable.close();

        if (this.arrayObjectId > 0) {
            RenderSystem.glDeleteVertexArrays(this.arrayObjectId);
            this.arrayObjectId = 0;
        }
    }
}