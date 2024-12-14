package rogo.sketchrender.culling;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import org.spongepowered.asm.mixin.Unique;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.CountBuffer;
import rogo.sketchrender.shader.uniform.SSBO;

public class ChunkCullingUniform {
    private int renderDistance = 0;
    private int spacePartitionSize = 0;
    public int queueUpdateCount = 0;
    public int lastQueueUpdateCount = 0;

    public static int[] sectionMaxElement = new int[]{393210};

    public static SSBO batchCommand;
    public static SSBO batchCounter;
    public static SSBO batchElement;
    public static CountBuffer cullingCounter;
    public static CountBuffer elementCounter;

    static {
        RenderSystem.recordRenderCall(() -> {
            batchCommand = new SSBO(IndirectCommandBuffer.INSTANCE);
            cullingCounter = new CountBuffer(VertexFormatElement.Type.INT);
            batchCounter = new SSBO(cullingCounter);
            elementCounter = new CountBuffer(VertexFormatElement.Type.INT);
            batchElement = new SSBO(elementCounter);
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
