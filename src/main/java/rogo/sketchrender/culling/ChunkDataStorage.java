package rogo.sketchrender.culling;

import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.util.IndexedQueue;

import java.nio.IntBuffer;

public class ChunkDataStorage {
    private static final int SECTION_DATA_STEP = 64;
    private static final int SECTIONS_PER_REGION = 256;
    private static final long REGION_DATA_STEP = SECTION_DATA_STEP * SECTIONS_PER_REGION;

    private final IndexedQueue<TerrainRenderPass> terrainRenderPassQueue = new IndexedQueue<>();
    private final IndexedQueue<SectionRenderDataStorage> sectionRenderDataQueue = new IndexedQueue<>();

    private final IntBuffer intBuffer;
    private boolean dirty = false;

    public ChunkDataStorage(int totalCapacity) {
        this.intBuffer = MemoryUtil.memAllocInt(totalCapacity);
        clearBuffer();
    }

    private void clearBuffer() {
        for (int i = 0; i < intBuffer.capacity(); i++) {
            intBuffer.put(i, 0);
        }
        dirty = false;
    }

    public int getRegionIndex(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage) {
        int passIndex = terrainRenderPassQueue.add(pass);
        int sectionIndex = sectionRenderDataQueue.add(sectionStorage);
        return passIndex * sectionRenderDataQueue.size() + sectionIndex;
    }

    public int getSectionOffset(int regionIndex, int sectionIndex) {
        return (int) (regionIndex * REGION_DATA_STEP + sectionIndex * SECTION_DATA_STEP);
    }

    public void setSliceMask(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int value) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex);
        intBuffer.put(offset, value);
        setDirty();
    }

    public int getSliceMask(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex);
        return intBuffer.get(offset);
    }

    public void setValid(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int value) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 4;
        intBuffer.put(offset, value);
        setDirty();
    }

    public int getValid(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 4;
        return intBuffer.get(offset);
    }

    public void setVertexOffset(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int facing, int value) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 8 + facing * 8;
        intBuffer.put(offset, value);
        setDirty();
    }

    public int getVertexOffset(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int facing) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 8 + facing * 8;
        return intBuffer.get(offset);
    }

    public void setElementCount(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int facing, int value) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 8 + facing * 8 + 4;
        intBuffer.put(offset, value);
        setDirty();
    }

    public int getElementCount(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int facing) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 8 + facing * 8 + 4;
        return intBuffer.get(offset);
    }

    private void setDirty() {
        dirty = true;
    }

    public void updateSSBO(int ssboId) {
        if (dirty) {
            intBuffer.flip(); // Prepare buffer for reading
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, intBuffer, GL43.GL_DYNAMIC_DRAW);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            dirty = false;
        }
    }

    public void free() {
        MemoryUtil.memFree(intBuffer);
    }
}