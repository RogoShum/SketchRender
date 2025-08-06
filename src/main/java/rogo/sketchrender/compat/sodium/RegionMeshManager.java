package rogo.sketchrender.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.shader.uniform.SSBO;
import rogo.sketchrender.util.IndexPool;

public class RegionMeshManager {
    private static final int SECTION_COUNT = 256;
    private static final int PASS_COUNT = 3;
    public static final long SECTION_DATA_SIZE = 92; // bytes
    public static final long PASS_DATA_SIZE = SECTION_DATA_SIZE * SECTION_COUNT;

    private final IndexPool<RenderRegion> regionIndex = new IndexPool<>();
    private SSBO meshDataBuffer;
    private long meshDataPointer;
    private int currentCapacity;

    public RegionMeshManager() {
        currentCapacity = 1;
        meshDataBuffer = new SSBO(1, SECTION_DATA_SIZE * SECTION_COUNT * PASS_COUNT, GL15.GL_DYNAMIC_DRAW);
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

    public boolean containRegion(RenderRegion region) {
        return regionIndex.contains(region);
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
    }

    public long getSectionMemPointer(RenderRegion region, int passIndex, int sectionIndex) {
        int regionIdx = regionIndex.indexOf(region);
        long sectionOffset = getSectionOffset(regionIdx, passIndex, sectionIndex);

        if (sectionOffset + SECTION_DATA_SIZE > meshDataBuffer.getCapacity()) {
            throw new RuntimeException("Out of capacity " + sectionOffset);
        }

        return meshDataPointer + sectionOffset;
    }

    public long getSectionOffset(int regionIndex, int passIndex, int sectionIndex) {
        return ((long) regionIndex * SECTION_COUNT * SECTION_DATA_SIZE * PASS_COUNT) +
                ((long) passIndex * SECTION_COUNT * SECTION_DATA_SIZE) +
                ((long) sectionIndex * SECTION_DATA_SIZE);
    }

    public void uploadSectionData(int regionIndex, int passIndex, int sectionIndex) {
        long offset = ((long) regionIndex * SECTION_COUNT * PASS_COUNT +
                (long) passIndex * SECTION_COUNT +
                sectionIndex);
        meshDataBuffer.upload(offset, (int) SECTION_DATA_SIZE);
    }

    public void uploadRegionPassData(int regionIndex, int passIndex) {
        long offset = ((long) regionIndex * PASS_COUNT +
                (long) passIndex);
        meshDataBuffer.upload(offset, (int) PASS_DATA_SIZE);
    }

    public void bindMeshData(int slot) {
        meshDataBuffer.bindShaderSlot(slot);
    }

    public int size() {
        return Math.max(currentCapacity, regionIndex.size());
    }

    public void refresh() {
        dispose();
        currentCapacity = 1;
        meshDataBuffer = new SSBO(1, SECTION_DATA_SIZE * SECTION_COUNT * PASS_COUNT, GL15.GL_DYNAMIC_DRAW);
        meshDataPointer = meshDataBuffer.getMemoryAddress();
    }

    public void dispose() {
        regionIndex.forEach((region, index) -> ((ExtraRenderRegion) region).refreshSectionData());
        meshDataBuffer.dispose();
        regionIndex.clear();
    }

    public long getBufferCapacity() {
        return meshDataBuffer.getCapacity();
    }
}