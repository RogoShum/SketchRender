package rogo.sketchrender.culling;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.SSBO;

public class ChunkCullingMessage {
    private int renderDistance = 0;
    private int spacePartitionSize = 0;
    public int queueUpdateCount = 0;
    public int lastQueueUpdateCount = 0;

    public static SSBO batchCulling;
    public static SSBO batchCommand;

    static {
        RenderSystem.recordRenderCall(() -> {
            batchCulling = new SSBO(ModelQuadFacing.COUNT * 256 + 1);
            batchCommand = new SSBO(IndirectCommandBuffer.INSTANCE);
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
