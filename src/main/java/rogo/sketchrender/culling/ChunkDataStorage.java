package rogo.sketchrender.culling;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.DataStorage;
import rogo.sketchrender.shader.uniform.SSBO;
import rogo.sketchrender.util.IndexedQueue;

import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChunkDataStorage {
    private static final int SECTION_DATA_STEP = 16;
    private static final int SECTIONS_PER_REGION = 256;
    private static final int REGION_DATA_STEP = SECTION_DATA_STEP * SECTIONS_PER_REGION;

    public static int sectionIndexTrace = -1;
    public static DataStorage dataStorageTrace = null;

    public static final Map<TerrainRenderPass, Integer> PASS_INDEX_MAP = IntStream.range(0, DefaultTerrainRenderPasses.ALL.length)
            .boxed()
            .collect(Collectors.toMap(i -> DefaultTerrainRenderPasses.ALL[i], i -> i));
    private final IndexedQueue<RenderRegion> renderRegionQueue = new IndexedQueue<>();
    public final SSBO regionIndex;

    private IntBuffer intBuffer;
    private boolean dirty = false;
    private boolean regionChanged = false;
    private int currentRegionSize = 0;

    public ChunkDataStorage(int regionSize) {
        int totalCapacity = regionSize * REGION_DATA_STEP * DefaultTerrainRenderPasses.ALL.length;
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

    public int getCurrentRegionSize() {
        return currentRegionSize;
    }

    public void addRenderRegion(RenderRegion renderRegion) {
        renderRegionQueue.add(renderRegion);

        if (renderRegionQueue.size() > currentRegionSize) {
            currentRegionSize++;
            intBuffer.position(0);
            intBuffer.limit(intBuffer.capacity());
            int totalCapacity = currentRegionSize * REGION_DATA_STEP * DefaultTerrainRenderPasses.ALL.length;
            IntBuffer newBuffer = MemoryUtil.memAllocInt(totalCapacity);
            newBuffer.put(intBuffer);
            MemoryUtil.memFree(intBuffer);
            this.intBuffer = newBuffer;
        }

        regionChanged = true;
    }

    public void addStorage(RenderRegion region, TerrainRenderPass pass, DataStorage storage) {
        storage.setRenderRegion(region);
        storage.setTerrainPass(pass);
        int index = getRegionIndex(storage) * REGION_DATA_STEP;
        storage.setStorageIndex(index);
    }

    public int getRegionIndex(DataStorage sectionStorage) {
        int passIndex = sectionStorage.getTerrainPass();
        int regionIndex = renderRegionQueue.add(sectionStorage.getRenderRegion());
        return regionIndex * PASS_INDEX_MAP.size() + passIndex;
    }

    public int getSectionOffset(DataStorage dataStorage, int sectionIndex) {
        return dataStorage.getStorageIndex() + sectionIndex * SECTION_DATA_STEP;
    }

    public void setSliceMask(int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        int offset = getSectionOffset(dataStorageTrace, sectionIndexTrace);
        intBuffer.put(offset, value);
        setDirty();
    }

    public void setValid(int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        int offset = getSectionOffset(dataStorageTrace, sectionIndexTrace) + 1;
        intBuffer.put(offset, value);
        setDirty();
    }

    public void setValid(int offset, int value) {
        intBuffer.put(offset, value);
        setDirty();
    }

    public void setVertexOffset(int facing, int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        int offset = getSectionOffset(dataStorageTrace, sectionIndexTrace);
        intBuffer.put(offset + 2 + facing * 2, value);

        setDirty();
    }

    public void setElementCount(int facing, int value) {
        if (sectionIndexTrace < 0 || dataStorageTrace == null) {
            return;
        }

        int offset = getSectionOffset(dataStorageTrace, sectionIndexTrace);
        intBuffer.put(offset + 3 + facing * 2, value);
        if (value > 0) {
            setValid(offset + 1, 1);
        }
        setDirty();
    }

    public void removeSection(DataStorage storage, int sections) {
        sectionIndexTrace = sections;
        dataStorageTrace = storage;
        setValid(0);
        sectionIndexTrace = -1;
        dataStorageTrace = null;
    }

    public void removeSectionStorage(DataStorage storage) {
        for (int i = 0; i < SECTIONS_PER_REGION; ++i) {
            int offset = getSectionOffset(storage, i) + 1;
            intBuffer.put(offset, 0);
        }

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
            Collection<Integer> regions = renderRegionQueue.getAllIndices();
            IntBuffer buffer = MemoryUtil.memAllocInt(regions.size() * 4);

            for (Integer index : regions) {
                RenderRegion region = renderRegionQueue.getObject(index);
                if (region != null) {
                    buffer.put(region.getChunkX());
                    buffer.put(region.getChunkY());
                    buffer.put(region.getChunkZ());
                    buffer.put(0);
                }
            }
            buffer.flip();

            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, regionIndex.getId());
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            MemoryUtil.memFree(buffer);
            regionChanged = false;
        }
    }

    public void free() {
        MemoryUtil.memFree(intBuffer);
        regionIndex.discard();
    }
}