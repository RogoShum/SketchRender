package rogo.sketch.compat.sodium;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL15;
import rogo.sketch.Config;
import rogo.sketch.render.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.render.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.render.resource.buffer.CounterBuffer;
import rogo.sketch.render.resource.buffer.PersistentReadSSBO;

public class MeshResource {
    private static int renderDistance = -1;
    private static int spacePartitionSize = 0;
    public static int queueUpdateCount = 0;
    public static int lastQueueUpdateCount = 0;
    public static int theoreticalRegionQuantity = 0;

    public static int currentFrame = 0;

    public static ShaderStorageBuffer batchCommand;
    public static ShaderStorageBuffer batchCounter;
    public static ShaderStorageBuffer batchRegionIndex;
    public static ShaderStorageBuffer batchMaxElement;
    public static PersistentReadSSBO maxElementPersistent;
    public static CounterBuffer cullingCounter;
    public static CounterBuffer elementCounter;

    public static final RegionMeshManager meshManager = new RegionMeshManager();

    static {
        batchCommand = new ShaderStorageBuffer(IndirectCommandBuffer.INSTANCE);
        cullingCounter = new CounterBuffer(VertexFormatElement.Type.INT);
        batchCounter = new ShaderStorageBuffer(cullingCounter);
        elementCounter = new CounterBuffer(VertexFormatElement.Type.INT);
        batchMaxElement = new ShaderStorageBuffer(elementCounter);
        batchRegionIndex = new ShaderStorageBuffer(1, 16, GL15.GL_DYNAMIC_DRAW);

        maxElementPersistent = new PersistentReadSSBO(1, Integer.BYTES);
    }

    public static void addIndexedRegion(RenderRegion region) {
        meshManager.addRegion(region);

        if (Config.getCullChunk()) {
            int regionSize = meshManager.size();
            int passSize = IndirectCommandBuffer.PASS_SIZE * regionSize;

            if (regionSize * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE * 20L > IndirectCommandBuffer.INSTANCE.getCapacity()) {
                IndirectCommandBuffer.INSTANCE.resize(meshManager.size() * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE);
                batchCommand.setBufferPointer(IndirectCommandBuffer.INSTANCE.getMemoryAddress());
                batchCommand.setCapacity(IndirectCommandBuffer.INSTANCE.getCapacity());
                batchCommand.resetUpload(GL15.GL_STATIC_DRAW);
            }

            if (passSize * cullingCounter.getStride() > cullingCounter.getCapacity()) {
                cullingCounter.resize(passSize);
                batchCounter.setBufferPointer(cullingCounter.getMemoryAddress());
                batchCounter.setCapacity(cullingCounter.getCapacity());
                batchCounter.resetUpload(GL15.GL_STATIC_DRAW);
            }

            batchRegionIndex.ensureCapacity(regionSize, true);
        }
    }

    public static void removeRegion(RenderRegion region) {
        meshManager.removeRegion(region);
    }

    public static void clearRegions() {
        meshManager.refresh();
        IndirectCommandBuffer.INSTANCE.resize(IndirectCommandBuffer.REGION_COMMAND_SIZE);
        cullingCounter.resize(1);
        batchRegionIndex.dispose();
        batchRegionIndex = new ShaderStorageBuffer(1, 16, GL15.GL_DYNAMIC_DRAW);
    }

    //TODO need fix
    public static void updateDistance(int renderDistance) {
        if (MeshResource.renderDistance != renderDistance) {
            MeshResource.renderDistance = renderDistance;
            spacePartitionSize = 2 * renderDistance + 1;

            if (Minecraft.getInstance().level != null && Config.getCullChunk()) {
                theoreticalRegionQuantity = (int) (spacePartitionSize * spacePartitionSize * Minecraft.getInstance().level.getSectionsCount() * 1.2 / 256);
                meshManager.initCapacity(theoreticalRegionQuantity);
            }
        }
    }

    public static int getRenderDistance() {
        return renderDistance;
    }

    public static int getSpacePartitionSize() {
        return spacePartitionSize;
    }
}