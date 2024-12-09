package rogo.sketchrender.culling;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.CountBuffer;
import rogo.sketchrender.shader.uniform.SSBO;

public class ChunkCullingUniform {
    private int renderDistance = 0;
    private int spacePartitionSize = 0;
    public int queueUpdateCount = 0;
    public int lastQueueUpdateCount = 0;

    public static SSBO chunkData;
    public static SSBO batchCommand;
    public static SSBO batchCounter;
    public static CountBuffer cullingCounter;

    static {
        RenderSystem.recordRenderCall(() -> {
            chunkData = new SSBO(4, 1);
            batchCommand = new SSBO(IndirectCommandBuffer.INSTANCE);
            cullingCounter = new CountBuffer(VertexFormatElement.Type.INT);
            batchCounter = new SSBO(cullingCounter);
        });
    }

    public void generateIndex(int renderDistance) {
        this.renderDistance = renderDistance;
        spacePartitionSize = 2 * renderDistance + 1;
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public int getSpacePartitionSize() {
        return spacePartitionSize;
    }
}
