package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.ShaderChunkRendererExt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.ExtraChunkRenderer;
import rogo.sketchrender.culling.ChunkCullingUniform;
import rogo.sketchrender.culling.ChunkRenderMixinHook;
import rogo.sketchrender.culling.CullingStateManager;
import rogo.sketchrender.shader.uniform.SSBO;

import java.nio.IntBuffer;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinDefaultChunkRenderer extends ShaderChunkRenderer implements ExtraChunkRenderer {
    Class<?> extraShaderInterface;
    boolean checkedShaderInterface = false;

    public MixinDefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }

    @Shadow
    protected abstract GlTessellation prepareTessellation(CommandList commandList, RenderRegion region);

    @Shadow
    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
    }

    @Shadow @Final private SharedQuadIndexBuffer sharedIndexBuffer;
    @Unique
    private int maxElementCount = 0;

    @Inject(method = "render", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void onRender(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, CallbackInfo ci) {
        if (Config.getCullChunk() && !CullingStateManager.SHADER_LOADER.renderingShaderPass()) {
            ci.cancel();
            super.begin(renderPass);

            ChunkShaderInterface shaderInterface = null;
            if (this.activeProgram != null) {
                shaderInterface = this.activeProgram.getInterface();
            } else {
                if (!checkedShaderInterface) {
                    try {
                        extraShaderInterface = Class.forName("net.irisshaders.iris.compat.sodium.impl.shader_overrides.ShaderChunkRendererExt");
                    } catch (ClassNotFoundException ignored) {
                    } finally {
                        checkedShaderInterface = true;
                    }
                } else if (extraShaderInterface != null && extraShaderInterface.isAssignableFrom(ShaderChunkRenderer.class)) {
                    GlProgram<IrisChunkShaderInterface> program = ((ShaderChunkRendererExt) this).iris$getOverride();
                    if (program != null) {
                        shaderInterface = program.getInterface();
                    }
                }
            }

            if (shaderInterface != null) {
                if (maxElementCount < ChunkCullingUniform.sectionMaxElement[0]) {
                    sharedIndexBuffer.ensureCapacity(commandList, ChunkCullingUniform.sectionMaxElement[0]);
                    maxElementCount = ChunkCullingUniform.sectionMaxElement[0];
                }

                ChunkRenderMixinHook.preRender(shaderInterface, matrices, renderPass);
                ChunkRenderMixinHook.onRender(this, sharedIndexBuffer, shaderInterface, commandList, renderLists, renderPass, camera);
            }

            super.end(renderPass);
        }
    }

    public GlTessellation sodiumTessellation(CommandList commandList, RenderRegion region) {
        return prepareTessellation(commandList, region);
    }

    public void sodiumModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        setModelMatrixUniforms(shader, region, camera);
    }
}
