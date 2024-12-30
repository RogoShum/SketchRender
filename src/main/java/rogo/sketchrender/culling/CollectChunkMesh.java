package rogo.sketchrender.culling;

import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;

public class CollectChunkMesh {
    public static void update(ChunkBuildContext buildContext, CancellationToken cancellationToken, ChunkBuildOutput chunkBuildOutput) {
        /*
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;
        }

        this.allocations[localSectionIndex] = allocation;
        long pMeshData = this.getDataPointer(localSectionIndex);
        int sliceMask = 0;
        int vertexOffset = chunkBuildOutput.;

        for(int facingIndex = 0; facingIndex < ModelQuadFacing.COUNT; ++facingIndex) {
            VertexRange vertexRange = ranges[facingIndex];
            int vertexCount;
            if (vertexRange != null) {
                vertexCount = vertexRange.vertexCount();
            } else {
                vertexCount = 0;
            }

            SectionRenderDataUnsafe.setVertexOffset(pMeshData, facingIndex, vertexOffset);
            SectionRenderDataUnsafe.setElementCount(pMeshData, facingIndex, (vertexCount >> 2) * 6);
            if (vertexCount > 0) {
                sliceMask |= 1 << facingIndex;
            }

            vertexOffset += vertexCount;
        }

        SectionRenderDataUnsafe.setSliceMask(pMeshData, sliceMask);
         */
    }
}
