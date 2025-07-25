package rogo.sketchrender.compat.sodium;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL15;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.CountBuffer;
import rogo.sketchrender.shader.uniform.PersistentReadSSBO;
import rogo.sketchrender.shader.uniform.SSBO;

/**
 * [Ugly-but-Working Buffer Design]
 * -------------------------------------------------
 * ! Seeking elegant alternatives !
 */
public class MeshUniform {
    private static int renderDistance = 0;
    private static int spacePartitionSize = 0;
    public static int queueUpdateCount = 0;
    public static int lastQueueUpdateCount = 0;
    public static int theoreticalRegionQuantity = 0;

    public static int currentFrame = 0;
    public static int[] sectionMaxElement = new int[]{ModelQuadFacing.COUNT * 256 + 1};

    public static SSBO batchCommand;
    public static SSBO batchCounter;
    public static SSBO batchRegionIndex;
    public static SSBO batchMaxElement;
    public static PersistentReadSSBO batchMaxElementPersistent;
    public static CountBuffer cullingCounter;
    public static CountBuffer elementCounter;
    public static CountBuffer elementCounterPersistent;

    public static final RegionMeshManager meshManager = new RegionMeshManager();

    static {
        batchCommand = new SSBO(IndirectCommandBuffer.INSTANCE);
        cullingCounter = new CountBuffer(VertexFormatElement.Type.INT);
        batchCounter = new SSBO(cullingCounter);
        elementCounter = new CountBuffer(VertexFormatElement.Type.INT);
        batchMaxElement = new SSBO(elementCounter);
        batchRegionIndex = new SSBO(1, 16, GL15.GL_DYNAMIC_DRAW);

        elementCounterPersistent = new CountBuffer(VertexFormatElement.Type.INT);
        batchMaxElementPersistent = new PersistentReadSSBO(1, Integer.BYTES);
    }

    public static void addIndexedRegion(RenderRegion region) {
        meshManager.addRegion(region);
        int regionSize = meshManager.size();
        int passSize = IndirectCommandBuffer.PASS_SIZE * regionSize;

        if (regionSize * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE * 20L > IndirectCommandBuffer.INSTANCE.getSize()) {
            IndirectCommandBuffer.INSTANCE.resize(meshManager.size() * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE);
            batchCommand.setBufferPointer(IndirectCommandBuffer.INSTANCE.getMemoryAddress());
            batchCommand.setCapacity(IndirectCommandBuffer.INSTANCE.getSize());
            batchCommand.resetUpload(GL15.GL_STATIC_DRAW);
        }

        if (passSize * cullingCounter.getStride() > cullingCounter.getSize()) {
            cullingCounter.resize(passSize);
            batchCounter.setBufferPointer(cullingCounter.getMemoryAddress());
            batchCounter.setCapacity(cullingCounter.getSize());
            batchCounter.resetUpload(GL15.GL_STATIC_DRAW);
        }

        batchRegionIndex.ensureCapacity(regionSize, true);
    }

    public static void removeRegion(RenderRegion region) {
        meshManager.removeRegion(region);
    }

    public static void clearRegions() {
        meshManager.refresh();
        IndirectCommandBuffer.INSTANCE.resize(IndirectCommandBuffer.REGION_COMMAND_SIZE);
        cullingCounter.resize(1);
        batchRegionIndex = new SSBO(1, 12, GL15.GL_DYNAMIC_DRAW);
    }

    public static void updateDistance(int renderDistance) {
        MeshUniform.renderDistance = renderDistance;
        spacePartitionSize = 2 * renderDistance + 1;

        if (Minecraft.getInstance().level != null) {
            theoreticalRegionQuantity = (int) (spacePartitionSize * spacePartitionSize * Minecraft.getInstance().level.getSectionsCount() * 1.2 / 256);
            meshManager.initCapacity(theoreticalRegionQuantity);
        }
    }

    public static int getRenderDistance() {
        return renderDistance;
    }

    public static int getSpacePartitionSize() {
        return spacePartitionSize;
    }
}