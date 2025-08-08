package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;

public interface ExtraChunkRenderer {
    GlVertexAttributeBinding[] getAttributeBindings();

    GlTessellation sodiumTessellation(CommandList commandList, RenderRegion region);

    void sodiumModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera);

    void setIndexedPass(boolean indexedPass);

    SharedQuadIndexBuffer getSharedIndexBuffer();
}