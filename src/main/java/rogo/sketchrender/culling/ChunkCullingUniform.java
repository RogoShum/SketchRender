package rogo.sketchrender.culling;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.CountBuffer;
import rogo.sketchrender.shader.uniform.SSBO;
import rogo.sketchrender.util.IndexPool;

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

    private static final IndexPool<RenderRegion> indexedRegions = new IndexPool<>();

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
        return indexedRegions.indexOf(region);
    }

    public static int addIndexedRegion(RenderRegion region) {
        indexedRegions.add(region);
        int index = indexedRegions.indexOf(region);
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
        int removedIndex = indexedRegions.indexOf(region);
        indexedRegions.remove(region);

        // 如果删除的不是最后一个元素，需要移动数据
        if (removedIndex < indexedRegions.size()) {
            // 计算每个区域的数据大小
            long regionDataSize = IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE;

            // 计算需要移动的数据大小（从removedIndex+1到末尾的所有数据）
            long dataToMove = (indexedRegions.size() - removedIndex) * regionDataSize;

            // 移动MeshData
            MemoryUtil.memCopy(
                    batchMeshData.getMemoryAddress() + ((removedIndex + 1) * regionDataSize), // 源地址：被删除位置的下一个位置
                    batchMeshData.getMemoryAddress() + (removedIndex * regionDataSize),       // 目标地址：被删除的位置
                    dataToMove                                                                // 要移动的数据大小
            );

            // 移动RegionIndex数据
            MemoryUtil.memCopy(
                    batchRegionIndex.getMemoryAddress() + ((removedIndex + 1) * batchRegionIndex.getStride()),
                    batchRegionIndex.getMemoryAddress() + (removedIndex * batchRegionIndex.getStride()),
                    (indexedRegions.size() - removedIndex) * batchRegionIndex.getStride()
            );

            // 更新SSBO
            batchMeshData.upload();
            batchRegionIndex.upload();

            // 如果数据量显著减少，可以考虑收缩缓冲区
            if (indexedRegions.size() * 2L < batchMeshData.getDataNum()) {
                int newSize = indexedRegions.size();
                int passSize = IndirectCommandBuffer.PASS_SIZE * newSize;

                // 调整缓冲区大小
                batchMeshData.ensureCapacity(passSize * IndirectCommandBuffer.REGION_COMMAND_SIZE);
                batchRegionIndex.ensureCapacity(newSize);
            }
        }
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
