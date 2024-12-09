package rogo.sketchrender.culling;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.shader.uniform.SSBO;
import rogo.sketchrender.util.IndexedQueue;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ChunkDataStorage {
    private static final int SECTION_DATA_STEP = 16;
    private static final int SECTIONS_PER_REGION = 256;
    private static final long REGION_DATA_STEP = SECTION_DATA_STEP * SECTIONS_PER_REGION;

    public static int sectionIndexTrace = -1;
    public static SectionRenderDataStorage dataStorageTrace = null;

    private final IndexedQueue<TerrainRenderPass> terrainRenderPassQueue = new IndexedQueue<>();
    private final IndexedQueue<RenderRegion> renderRegionQueue = new IndexedQueue<>();
    private final Map<SectionRenderDataStorage, RenderRegion> sectionStorageMap = new HashMap<>();
    private final Map<SectionRenderDataStorage, TerrainRenderPass> sectionPassMap = new HashMap<>();
    public final SSBO regionIndex;

    private IntBuffer intBuffer;
    private boolean dirty = false;
    private boolean regionChanged = false;
    private int currentRegionSize = 0;

    public ChunkDataStorage(int regionSize) {
        int totalCapacity = regionSize * SECTION_DATA_STEP * SECTIONS_PER_REGION * DefaultTerrainRenderPasses.ALL.length;
        this.intBuffer = MemoryUtil.memAllocInt(totalCapacity);
        clearBuffer();
        currentRegionSize = regionSize;
        regionIndex = new SSBO(1, 1);
    }

    private void clearBuffer() {
        for (int i = 0; i < intBuffer.capacity(); i++) {
            intBuffer.put(i, 0);
        }
        dirty = false;
    }

    public Collection<RenderRegion> getRenderRegions() {
        return renderRegionQueue.getAllObjects();
    }

    public int getCurrentRegionSize() {
        return currentRegionSize;
    }

    public void addRenderRegion(RenderRegion renderRegion) {
        Arrays.stream(DefaultTerrainRenderPasses.ALL).forEach(pass -> {
            SectionRenderDataStorage dataStorage = renderRegion.createStorage(pass);
            sectionStorageMap.put(dataStorage, renderRegion);
            sectionPassMap.put(dataStorage, pass);
        });

        renderRegionQueue.add(renderRegion);

        if (renderRegionQueue.size() > currentRegionSize) {
            currentRegionSize++;
            intBuffer.position(0);
            intBuffer.limit(intBuffer.capacity());
            int totalCapacity = currentRegionSize * SECTION_DATA_STEP * SECTIONS_PER_REGION * DefaultTerrainRenderPasses.ALL.length;
            IntBuffer newBuffer = MemoryUtil.memAllocInt(totalCapacity);
            newBuffer.put(intBuffer);
            MemoryUtil.memFree(intBuffer);
            this.intBuffer = newBuffer;
        }

        regionChanged = true;
    }

    public int getRegionIndex(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage) {
        if (!sectionStorageMap.containsKey(sectionStorage)) {
            throw new RuntimeException("Invalid section storage: " + sectionStorage);
        }

        int passIndex = terrainRenderPassQueue.add(pass);
        int regionIndex = renderRegionQueue.add(sectionStorageMap.get(sectionStorage));
        return regionIndex * terrainRenderPassQueue.size() + passIndex;
    }

    public int getSectionOffset(int regionIndex, int sectionIndex) {
        return (int) (regionIndex * REGION_DATA_STEP + sectionIndex * SECTION_DATA_STEP);
    }

    public void setSliceMask(int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        TerrainRenderPass pass = sectionPassMap.get(dataStorageTrace);
        int regionIndex = getRegionIndex(pass, dataStorageTrace);
        int offset = getSectionOffset(regionIndex, sectionIndexTrace);
        intBuffer.put(offset, value);
        setDirty();
    }

    public int getSliceMask(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex);
        return intBuffer.get(offset);
    }

    public void setValid(int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        TerrainRenderPass pass = sectionPassMap.get(dataStorageTrace);
        int regionIndex = getRegionIndex(pass, dataStorageTrace);
        int offset = getSectionOffset(regionIndex, sectionIndexTrace) + 1;
        intBuffer.put(offset, value);
        setDirty();
    }

    public void setValid(int offset, int value) {
        intBuffer.put(offset, value);
        setDirty();
    }

    public int getValid(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 1;
        return intBuffer.get(offset);
    }

    public void setVertexOffset(int facing, int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        TerrainRenderPass pass = sectionPassMap.get(dataStorageTrace);
        int regionIndex = getRegionIndex(pass, dataStorageTrace);
        int offset = getSectionOffset(regionIndex, sectionIndexTrace);
        intBuffer.put(offset + 2 + facing * 2, value);

        setDirty();
    }

    public int getVertexOffset(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int facing) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 2 + facing * 2;
        return intBuffer.get(offset);
    }

    public void setElementCount(int facing, int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        TerrainRenderPass pass = sectionPassMap.get(dataStorageTrace);
        int regionIndex = getRegionIndex(pass, dataStorageTrace);
        int offset = getSectionOffset(regionIndex, sectionIndexTrace);
        intBuffer.put(offset + 3 + facing * 2, value);
        if (value > 0) {
            setValid(offset + 1, 1);
        }
        setDirty();
    }

    public int getElementCount(TerrainRenderPass pass, SectionRenderDataStorage sectionStorage, int sectionIndex, int facing) {
        int regionIndex = getRegionIndex(pass, sectionStorage);
        int offset = getSectionOffset(regionIndex, sectionIndex) + 3 + facing * 2;
        return intBuffer.get(offset);
    }

    public void removeSection(SectionRenderDataStorage storage, int sections) {
        sectionIndexTrace = sections;
        dataStorageTrace = storage;
        setValid(0);
        sectionIndexTrace = -1;
        dataStorageTrace = null;
    }

    public void removeSectionStorage(SectionRenderDataStorage storage) {
        TerrainRenderPass pass = sectionPassMap.get(storage);
        int regionIndex = getRegionIndex(pass, storage);

        for (int i = 0; i < SECTIONS_PER_REGION; ++i) {
            int offset = getSectionOffset(regionIndex, i) + 1;
            intBuffer.put(offset, 0);
        }

        sectionStorageMap.remove(storage);
        sectionPassMap.remove(storage);

        setDirty();
    }

    public void deleteRegion(RenderRegion region) {
        renderRegionQueue.remove(region);
    }

    private void setDirty() {
        dirty = true;
    }

    public void updateSSBO(int ssboId) {
        if (dirty) {
            intBuffer.position(0);
            intBuffer.limit(intBuffer.capacity());
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, intBuffer, GL43.GL_DYNAMIC_DRAW);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            dirty = false;
        }

        if (regionChanged) {

            regionChanged = false;
        }

        Collection<Integer> regions = renderRegionQueue.getAllIndices();
        IntBuffer buffer = MemoryUtil.memAllocInt(regions.size() * 3);

        for (Integer index : regions) {
            RenderRegion region = renderRegionQueue.getObject(index);
            if (region != null) {
                buffer.put(region.getChunkX());
                buffer.put(region.getChunkY());
                buffer.put(region.getChunkZ());
            }
        }
        buffer.flip();

        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, regionIndex.getId());
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        MemoryUtil.memFree(buffer);
    }

    public void free() {
        MemoryUtil.memFree(intBuffer);
        regionIndex.discard();
    }
}