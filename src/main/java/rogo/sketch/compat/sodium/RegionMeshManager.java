package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.compat.sodium.api.ExtraRenderRegion;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.MemoryLayout;
import rogo.sketch.render.data.format.Std430DataFormat;
import rogo.sketch.render.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.util.IndexPool;

public class RegionMeshManager {
    private static final DataFormat SECTION_DATA_FORMAT = Std430DataFormat.std430Builder("SectionData")
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
    private ShaderStorageBuffer meshDataBuffer;
    private MemoryLayout memoryLayout;
    private long meshDataPointer;
    private int currentCapacity;

    public RegionMeshManager() {
        currentCapacity = 1;
        initializeMemoryLayout();
        initializeBuffer();
    }

    private void initializeMemoryLayout() {
        this.memoryLayout = MemoryLayout.builder(SECTION_DATA_FORMAT)
                .addDimension("region", currentCapacity)
                .addDimension("pass", PASS_COUNT)
                .addDimension("section", SECTION_COUNT)
                .build();
    }

    private void initializeBuffer() {
        meshDataBuffer = new ShaderStorageBuffer(1, SECTION_DATA_SIZE * SECTION_COUNT * PASS_COUNT, GL15.GL_DYNAMIC_DRAW);
        meshDataPointer = meshDataBuffer.getMemoryAddress();
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
        long newPointer = MemoryUtil.nmemCalloc(requiredCapacity, meshDataBuffer.getStride());

        MemoryUtil.memCopy(meshDataPointer, newPointer, meshDataBuffer.getCapacity());
        MemoryUtil.nmemFree(meshDataPointer);

        meshDataPointer = newPointer;
        currentCapacity = requiredCapacity;

        meshDataBuffer.setBufferPointer(meshDataPointer);
        meshDataBuffer.setCapacity(requiredCapacity * meshDataBuffer.getStride());
        meshDataBuffer.resetUpload(GL15.GL_DYNAMIC_DRAW);

        initializeMemoryLayout();
    }

    public long getSectionMemPointer(RenderRegion region, int passIndex, int sectionIndex) {
        int regionIdx = regionIndex.indexOf(region);
        long byteOffset = memoryLayout.calculateByteOffset(regionIdx, passIndex, sectionIndex);

        if (byteOffset + SECTION_DATA_SIZE > meshDataBuffer.getCapacity()) {
            throw new RuntimeException("Out of capacity " + byteOffset);
        }

        return meshDataPointer + byteOffset;
    }

    public void uploadSectionData(int regionIndex, int passIndex, int sectionIndex) {
        long elementOffset = memoryLayout.calculateElementOffset(regionIndex, passIndex, sectionIndex);
        meshDataBuffer.upload(elementOffset, (int) SECTION_DATA_SIZE);
    }

    public void uploadRegionPassData(int regionIndex, int passIndex) {
        long passElementOffset = ((long) regionIndex * PASS_COUNT + (long) passIndex);
        long passDataSize = memoryLayout.getDataSize("section");

        meshDataBuffer.upload(passElementOffset, (int) passDataSize);
    }

    public long getPassDataSize() {
        return memoryLayout.getDataSize("pass");
    }

    public long getRegionDataSize() {
        return memoryLayout.getDataSize("region");
    }

    public long getSectionSize() {
        return memoryLayout.getDataSize("section");
    }

    public int size() {
        return Math.max(currentCapacity, regionIndex.size());
    }

    public void refresh() {
        dispose();
        currentCapacity = 1;
        initializeMemoryLayout();
        initializeBuffer();
    }

    public void dispose() {
        regionIndex.forEach((region, index) -> ((ExtraRenderRegion) region).refreshSectionData());
        if (meshDataBuffer != null) {
            meshDataBuffer.dispose();
        }
        regionIndex.clear();
    }

    public ShaderStorageBuffer meshDataBuffer() {
        return meshDataBuffer;
    }

    public MemoryLayout getMemoryLayout() {
        return memoryLayout;
    }

    public DataFormat getSectionDataFormat() {
        return SECTION_DATA_FORMAT;
    }
}