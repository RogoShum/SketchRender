package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import rogo.sketchrender.api.ExtraChunkRenderer;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinDefaultChunkRenderer extends ShaderChunkRenderer implements ExtraChunkRenderer {

    public MixinDefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }

    @Shadow
    protected abstract GlTessellation prepareTessellation(CommandList commandList, RenderRegion region);

    @Shadow
    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
    }

    public GlTessellation sodiumTessellation(CommandList commandList, RenderRegion region) {
        return prepareTessellation(commandList, region);
    }

    public void sodiumModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        setModelMatrixUniforms(shader, region, camera);
    }
}
