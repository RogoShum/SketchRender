package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.compat.sodium.api.ExtraRenderRegion;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.data.format.MemoryLayout;
import rogo.sketch.core.data.layout.Std430StructLayout;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.util.IndexPool;

/**
 * Manages mesh data for Sodium regions using SSBOs.
 * Updated to use new DataBufferWriter and MemoryLayout tools.
 */
public class RegionMeshManager {
    private static final StructLayout SECTION_DATA_FORMAT = Std430StructLayout.std430Builder("SectionData")
            .intElement("mask")
            .intElement("visibility")
            .intElement("mesh_0_vertex_offset").intElement("mesh_0_element_count").intElement("mesh_0_index_offset")
            .intElement("mesh_1_vertex_offset").intElement("mesh_1_element_count").intElement("mesh_1_index_offset")
            .intElement("mesh_2_vertex_offset").intElement("mesh_2_element_count").intElement("mesh_2_index_offset")
            .intElement("mesh_3_vertex_offset").intElement("mesh_3_element_count").intElement("mesh_3_index_offset")
            .intElement("mesh_4_vertex_offset").intElement("mesh_4_element_count").intElement("mesh_4_index_offset")
            .intElement("mesh_5_vertex_offset").intElement("mesh_5_element_count").intElement("mesh_5_index_offset")
            .intElement("mesh_6_vertex_offset").intElement("mesh_6_element_count").intElement("mesh_6_index_offset")
            .build();

    private static final int SECTION_COUNT = 256;
    private static final int PASS_COUNT = 3;
    public static final long SECTION_DATA_SIZE = SECTION_DATA_FORMAT.getStride();

    private final IndexPool<RenderRegion> regionIndex = new IndexPool<>();
    private BackendStorageBuffer meshDataBuffer;
    private MemoryLayout memoryLayout;
    private long meshDataPointer;
    private int currentCapacity;

    public RegionMeshManager() {
        currentCapacity = 1;
        initializeMemoryLayout();
    }

    private void initializeMemoryLayout() {
        this.memoryLayout = MemoryLayout.builder(SECTION_DATA_FORMAT)
                .addDimension("region", currentCapacity)
                .addDimension("pass", PASS_COUNT)
                .addDimension("section", SECTION_COUNT)
                .build();
    }

    public void attachMeshDataBuffer(BackendStorageBuffer meshDataBuffer) {
        this.meshDataBuffer = meshDataBuffer;
        syncMeshDataBufferCapacity(false);
    }

    public int indexOf(RenderRegion region) {
        if (!regionIndex.contains(region)) {
            regionIndex.add(region);
        }
        return regionIndex.indexOf(region);
    }

    public void addRegion(RenderRegion region) {
        regionIndex.add(region);
        int index = regionIndex.indexOf(region);

        if (index >= currentCapacity) {
            expandCapacity((int) ((currentCapacity + 1) * 1.2));
        }
    }

    public void initCapacity(int capacity) {
        if (capacity > currentCapacity) {
            expandCapacity(capacity);
        }
    }

    public void removeRegion(RenderRegion region) {
        regionIndex.remove(region);
    }

    private void expandCapacity(int requiredCapacity) {
        currentCapacity = requiredCapacity;
        initializeMemoryLayout();
        syncMeshDataBufferCapacity(true);
    }

    /**
     * Get the raw memory pointer for a section.
     * Use getSectionWriter() for safer access.
     */
    public long getSectionMemPointer(RenderRegion region, int passIndex, int sectionIndex) {
        if (meshDataBuffer == null || meshDataBuffer.isDisposed()) {
            throw new IllegalStateException("Terrain mesh data buffer has not been attached");
        }
        int regionIdx = regionIndex.indexOf(region);
        long byteOffset = memoryLayout.calculateByteOffset(regionIdx, passIndex, sectionIndex);

        if (byteOffset + SECTION_DATA_SIZE > meshDataBuffer.capacityBytes()) {
            throw new RuntimeException("Out of capacity " + byteOffset);
        }

        return meshDataPointer + byteOffset;
    }

    public void uploadSectionData(int regionIndex, int passIndex, int sectionIndex) {
        if (meshDataBuffer == null || meshDataBuffer.isDisposed()) {
            return;
        }
        long elementOffset = memoryLayout.calculateElementOffset(regionIndex, passIndex, sectionIndex);
        meshDataBuffer.upload(elementOffset, (int) SECTION_DATA_SIZE);
    }

    public void uploadRegionPassData(int regionIndex, int passIndex) {
        if (meshDataBuffer == null || meshDataBuffer.isDisposed()) {
            return;
        }
        long passElementOffset = ((long) regionIndex * PASS_COUNT + (long) passIndex);
        long passDataSize = memoryLayout.getDataSize("pass");

        meshDataBuffer.upload(passElementOffset, (int) passDataSize);
    }

    public long getPassDataSize() {
        return memoryLayout.getDataSize("pass");
    }

    public long getRegionDataSize() {
        return memoryLayout.getDataSize("region");
    }

    public long getSectionSize() {
        return SECTION_DATA_SIZE;
    }

    public int size() {
        return Math.max(currentCapacity, regionIndex.size());
    }

    public int regionCount() {
        return regionIndex.size();
    }

    public int currentCapacity() {
        return currentCapacity;
    }

    public void refresh() {
        clearMeshData();
        regionIndex.forEach((region, index) -> ((ExtraRenderRegion) region).refreshSectionData());
        regionIndex.clear();
        currentCapacity = 1;
        initializeMemoryLayout();
        syncMeshDataBufferCapacity(false);
    }

    public void dispose() {
        regionIndex.forEach((region, index) -> ((ExtraRenderRegion) region).refreshSectionData());
        regionIndex.clear();
        meshDataBuffer = null;
        meshDataPointer = 0L;
    }

    public BackendStorageBuffer meshDataBuffer() {
        return meshDataBuffer;
    }

    public MemoryLayout getMemoryLayout() {
        return memoryLayout;
    }

    public StructLayout getSectionDataFormat() {
        return SECTION_DATA_FORMAT;
    }

    private void syncMeshDataBufferCapacity(boolean copy) {
        if (meshDataBuffer == null || meshDataBuffer.isDisposed()) {
            meshDataPointer = 0L;
            return;
        }
        int requiredElements = Math.max(1, currentCapacity) * PASS_COUNT * SECTION_COUNT;
        meshDataBuffer.ensureCapacity(requiredElements, copy);
        meshDataPointer = meshDataBuffer.memoryAddress();
    }

    private void clearMeshData() {
        if (meshDataBuffer == null || meshDataBuffer.isDisposed() || meshDataBuffer.memoryAddress() == 0L) {
            return;
        }
        MemoryUtil.memSet(meshDataBuffer.memoryAddress(), 0, meshDataBuffer.capacityBytes());
        meshDataBuffer.position(0L);
        meshDataBuffer.upload();
    }
}

