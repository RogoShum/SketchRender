package rogo.sketchrender.culling;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL15;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.CountBuffer;
import rogo.sketchrender.shader.uniform.SSBO;
import rogo.sketchrender.util.IndexedQueue;

public class ChunkCullingUniform {
    private static int renderDistance = 0;
    private static int spacePartitionSize = 0;
    public static int queueUpdateCount = 0;
    public static int lastQueueUpdateCount = 0;

    public static int currentFrame = 0;
    public static int[] sectionMaxElement = new int[]{393210};

    public static SSBO batchCommand;
    public static SSBO batchCounter;
    public static SSBO batchMeshData;
    public static SSBO batchRegionIndex;
    public static SSBO batchElement;
    public static CountBuffer cullingCounter;
    public static CountBuffer elementCounter;

    private static int regionX;
    private static int regionY;
    private static int regionZ;

    private static final IndexedQueue<BlockPos> indexedRegions = new IndexedQueue<>();

    static {
        RenderSystem.recordRenderCall(() -> {
            batchCommand = new SSBO(IndirectCommandBuffer.INSTANCE);
            cullingCounter = new CountBuffer(VertexFormatElement.Type.INT);
            batchCounter = new SSBO(cullingCounter);
            elementCounter = new CountBuffer(VertexFormatElement.Type.INT);
            batchElement = new SSBO(elementCounter);
            batchMeshData = new SSBO(IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE, 64, GL15.GL_DYNAMIC_DRAW);
            batchRegionIndex = new SSBO(1, 16, GL15.GL_DYNAMIC_DRAW);
        });
    }

    public static int getRegionIndex(RenderRegion region) {
        return indexedRegions.add(new BlockPos(region.getChunkX(), region.getChunkY(), region.getChunkZ()));
    }

    public static int addIndexedRegion(RenderRegion region) {
        int index = indexedRegions.add(new BlockPos(region.getChunkX(), region.getChunkY(), region.getChunkZ()));
        int regionSize = indexedRegions.size();
        int passSize = IndirectCommandBuffer.PASS_SIZE * regionSize;

        if (regionSize * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE * 20L > IndirectCommandBuffer.INSTANCE.getSize()) {
            IndirectCommandBuffer.INSTANCE.resize(indexedRegions.size() * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE);
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

        batchMeshData.ensureCapacity(passSize * IndirectCommandBuffer.REGION_COMMAND_SIZE);
        batchRegionIndex.ensureCapacity(regionSize);

        return index;
    }

    public static void removeIndexedRegion(RenderRegion region) {
        indexedRegions.remove(new BlockPos(region.getChunkX(), region.getChunkY(), region.getChunkZ()));
    }

    public static void clearRegions() {
        indexedRegions.clear();
        IndirectCommandBuffer.INSTANCE.resize(IndirectCommandBuffer.REGION_COMMAND_SIZE);
        cullingCounter.resize(1);
        batchMeshData.discard();
        batchMeshData = new SSBO(IndirectCommandBuffer.REGION_COMMAND_SIZE, 64, GL15.GL_DYNAMIC_DRAW);
        batchRegionIndex = new SSBO(1, 12, GL15.GL_DYNAMIC_DRAW);
    }

    public static void updateDistance(int renderDistance) {
        ChunkCullingUniform.renderDistance = renderDistance;
        spacePartitionSize = 2 * renderDistance + 1;
    }

    public static int getRenderDistance() {
        return renderDistance;
    }

    public static int getSpacePartitionSize() {
        return spacePartitionSize;
    }

    public static void switchRegion(int x, int y, int z) {
        regionX = x;
        regionY = y;
        regionZ = z;
    }

    public static BlockPos getRegionPos() {
        return new BlockPos(regionX, regionY, regionZ);
    }
}
