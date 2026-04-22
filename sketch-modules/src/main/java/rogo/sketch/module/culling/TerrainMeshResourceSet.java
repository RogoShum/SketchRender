package rogo.sketch.module.culling;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendBufferFactory;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

/**
 * Core-owned terrain mesh resource owner.
 * Phase B moves the actual backend allocations here so mod/Sodium only provide
 * region indexing and mesh-data writes.
 */
public final class TerrainMeshResourceSet {
    public static final long REGION_COMMAND_SIZE = 7L * 256L + 1L;
    public static final int PASS_SIZE = 3;
    public static final int SECTION_COUNT = 256;
    public static final long REGION_PASS_COMMAND_SIZE = REGION_COMMAND_SIZE * PASS_SIZE;
    public static final long REGION_INDEX_STRIDE_BYTES = 16L;
    public static final long COUNTER_STRIDE_BYTES = Integer.BYTES;

    private static final TerrainMeshResourceSet INSTANCE = new TerrainMeshResourceSet();
    private static final KeyId TERRAIN_MESH_DATA_BUFFER_ID = KeyId.of("sketch_render", "terrain_mesh_data_buffer");
    private static final KeyId TERRAIN_REGION_INDEX_BUFFER_ID = KeyId.of("sketch_render", "terrain_region_index_buffer");
    private static final KeyId TERRAIN_INDIRECT_COMMAND_BUFFER_ID = KeyId.of("sketch_render", "terrain_indirect_command_buffer");
    private static final KeyId TERRAIN_CULL_COUNTER_BUFFER_ID = KeyId.of("sketch_render", "terrain_cull_counter_buffer");
    private static final KeyId TERRAIN_ELEMENT_COUNTER_BUFFER_ID = KeyId.of("sketch_render", "terrain_element_counter_buffer");
    private static final KeyId TERRAIN_MAX_ELEMENT_READBACK_BUFFER_ID = KeyId.of("sketch_render", "terrain_max_element_readback_buffer");

    private volatile int renderDistance = -1;
    private volatile int spacePartitionSize = 0;
    private volatile int queueUpdateCount = 0;
    private volatile int lastQueueUpdateCount = 0;
    private volatile int theoreticalRegionQuantity = 0;
    private volatile int currentFrame = 0;
    private volatile int orderedRegionSize = 0;
    private volatile long meshDataStrideBytes = 0L;

    private volatile BackendStorageBuffer meshDataBuffer;
    private volatile BackendStorageBuffer regionIndexBuffer;
    private volatile TerrainMeshIndirectBuffer indirectCommands;
    private volatile TerrainMeshCounterBuffer cullingCounter;
    private volatile TerrainMeshCounterBuffer elementCounter;
    private volatile TerrainMeshReadbackBuffer maxElementReadbackBuffer;

    private TerrainMeshResourceSet() {
    }

    public static TerrainMeshResourceSet getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureCoreResources() {
        if (!GraphicsDriver.isBootstrapped()) {
            return;
        }
        if (regionIndexBuffer == null || regionIndexBuffer.isDisposed()) {
            regionIndexBuffer = BackendBufferFactory.createStorageBuffer(
                    TERRAIN_REGION_INDEX_BUFFER_ID,
                    regionIndexDescriptor(1),
                    null);
        }
        if (indirectCommands == null || indirectCommands.isDisposed()) {
            indirectCommands = (TerrainMeshIndirectBuffer) BackendBufferFactory.createIndirectBuffer(
                    TERRAIN_INDIRECT_COMMAND_BUFFER_ID,
                    indirectCommandDescriptor(REGION_COMMAND_SIZE),
                    REGION_COMMAND_SIZE);
        }
        if (cullingCounter == null || cullingCounter.isDisposed()) {
            cullingCounter = (TerrainMeshCounterBuffer) BackendBufferFactory.createCounterBuffer(
                    TERRAIN_CULL_COUNTER_BUFFER_ID,
                    counterDescriptor(TERRAIN_CULL_COUNTER_BUFFER_ID, 1),
                    null);
        }
        if (elementCounter == null || elementCounter.isDisposed()) {
            elementCounter = (TerrainMeshCounterBuffer) BackendBufferFactory.createCounterBuffer(
                    TERRAIN_ELEMENT_COUNTER_BUFFER_ID,
                    counterDescriptor(TERRAIN_ELEMENT_COUNTER_BUFFER_ID, 1),
                    null);
        }
        if (maxElementReadbackBuffer == null || maxElementReadbackBuffer.isDisposed()) {
            maxElementReadbackBuffer = (TerrainMeshReadbackBuffer) BackendBufferFactory.createReadbackBuffer(
                    TERRAIN_MAX_ELEMENT_READBACK_BUFFER_ID,
                    readbackDescriptor(1),
                    1);
        }
    }

    public synchronized BackendStorageBuffer ensureMeshDataBuffer(long sectionStrideBytes, int regionCapacity) {
        ensureCoreResources();
        if (!GraphicsDriver.isBootstrapped()) {
            return meshDataBuffer;
        }
        long normalizedStride = Math.max(1L, sectionStrideBytes);
        int normalizedRegions = Math.max(regionCapacity, 1);
        int requiredElements = requiredMeshElementCount(normalizedRegions);
        if (meshDataBuffer == null || meshDataBuffer.isDisposed() || meshDataStrideBytes != normalizedStride) {
            if (meshDataBuffer != null && !meshDataBuffer.isDisposed()) {
                meshDataBuffer.dispose();
            }
            meshDataStrideBytes = normalizedStride;
            meshDataBuffer = BackendBufferFactory.createStorageBuffer(
                    TERRAIN_MESH_DATA_BUFFER_ID,
                    meshDataDescriptor(requiredElements, normalizedStride),
                    null);
            return meshDataBuffer;
        }
        meshDataBuffer.ensureCapacity(requiredElements, true);
        return meshDataBuffer;
    }

    public synchronized void disposeOwnedResources() {
        disposeQuietly(meshDataBuffer);
        disposeQuietly(regionIndexBuffer);
        disposeQuietly(indirectCommands);
        disposeQuietly(cullingCounter);
        disposeQuietly(elementCounter);
        disposeQuietly(maxElementReadbackBuffer);
        meshDataBuffer = null;
        regionIndexBuffer = null;
        indirectCommands = null;
        cullingCounter = null;
        elementCounter = null;
        maxElementReadbackBuffer = null;
        meshDataStrideBytes = 0L;
        orderedRegionSize = 0;
    }

    public void updateDistance(int newRenderDistance, int sectionsCount) {
        if (renderDistance == newRenderDistance) {
            return;
        }
        renderDistance = newRenderDistance;
        spacePartitionSize = 2 * newRenderDistance + 1;
        theoreticalRegionQuantity = (int) (spacePartitionSize * spacePartitionSize * sectionsCount * 1.2 / 256);
    }

    public synchronized void onRegionCapacityChanged(int regionCapacity) {
        if (regionCapacity <= 0) {
            return;
        }

        ensureCoreResources();

        TerrainMeshIndirectBuffer indirectCommands = indirectCommands();
        if (indirectCommands != null
                && regionCapacity * REGION_PASS_COMMAND_SIZE * BackendStorageBufferCommandStride.INDIRECT_COMMAND_BYTES
                > indirectCommands.getCapacity()) {
            indirectCommands.resize(Math.max(regionCapacity * REGION_PASS_COMMAND_SIZE, REGION_COMMAND_SIZE));
        }

        TerrainMeshCounterBuffer cullingCounter = cullingCounter();
        if (cullingCounter != null) {
            int passCounterCount = Math.max(regionCapacity * PASS_SIZE, 1);
            if (passCounterCount * cullingCounter.getStride() > cullingCounter.getCapacity()) {
                cullingCounter.resize(passCounterCount);
            }
        }

        BackendStorageBuffer regionIndexBuffer = regionIndexBuffer();
        if (regionIndexBuffer != null) {
            regionIndexBuffer.ensureCapacity(Math.max(regionCapacity, 1), true);
        }

        BackendStorageBuffer meshDataBuffer = meshDataBuffer();
        if (meshDataBuffer != null && meshDataStrideBytes > 0L) {
            meshDataBuffer.ensureCapacity(requiredMeshElementCount(regionCapacity), true);
        }
    }

    public synchronized void clearRegions() {
        ensureCoreResources();
        orderedRegionSize = 0;
        TerrainMeshIndirectBuffer indirectCommands = indirectCommands();
        if (indirectCommands != null) {
            indirectCommands.resize(REGION_COMMAND_SIZE);
            indirectCommands.clear();
        }

        TerrainMeshCounterBuffer cullingCounter = cullingCounter();
        if (cullingCounter != null) {
            cullingCounter.resize(1);
            cullingCounter.updateCount(0);
        }

        TerrainMeshCounterBuffer elementCounter = elementCounter();
        if (elementCounter != null) {
            elementCounter.resize(1);
            elementCounter.updateCount(0);
        }

        TerrainMeshReadbackBuffer readbackBuffer = maxElementReadbackBuffer();
        if (readbackBuffer != null) {
            readbackBuffer.ensureCapacity(1, true);
        }

        BackendStorageBuffer regionIndexBuffer = regionIndexBuffer();
        if (regionIndexBuffer != null) {
            regionIndexBuffer.ensureCapacity(1, false, true);
            regionIndexBuffer.position(0L);
        }

        BackendStorageBuffer meshDataBuffer = meshDataBuffer();
        if (meshDataBuffer != null && meshDataBuffer.memoryAddress() != 0L && meshDataBuffer.capacityBytes() > 0L) {
            org.lwjgl.system.MemoryUtil.memSet(meshDataBuffer.memoryAddress(), 0, meshDataBuffer.capacityBytes());
            meshDataBuffer.position(0L);
            meshDataBuffer.upload();
        }
    }

    public void incrementQueueUpdateCount() {
        queueUpdateCount++;
    }

    public void rollQueueUpdateCounters() {
        lastQueueUpdateCount = queueUpdateCount;
        queueUpdateCount = 0;
    }

    public int renderDistance() {
        return renderDistance;
    }

    public int spacePartitionSize() {
        return spacePartitionSize;
    }

    public int queueUpdateCount() {
        return queueUpdateCount;
    }

    public int lastQueueUpdateCount() {
        return lastQueueUpdateCount;
    }

    public int theoreticalRegionQuantity() {
        return theoreticalRegionQuantity;
    }

    public int currentFrame() {
        return currentFrame;
    }

    public void setCurrentFrame(int frame) {
        currentFrame = frame;
    }

    public int orderedRegionSize() {
        return orderedRegionSize;
    }

    public void setOrderedRegionSize(int size) {
        orderedRegionSize = Math.max(size, 0);
    }

    @Nullable
    public BackendStorageBuffer meshDataBuffer() {
        return meshDataBuffer;
    }

    @Nullable
    public BackendStorageBuffer regionIndexBuffer() {
        return regionIndexBuffer;
    }

    @Nullable
    public TerrainMeshReadbackBuffer maxElementReadbackBuffer() {
        return maxElementReadbackBuffer;
    }

    @Nullable
    public TerrainMeshCounterBuffer cullingCounter() {
        return cullingCounter;
    }

    @Nullable
    public TerrainMeshCounterBuffer elementCounter() {
        return elementCounter;
    }

    @Nullable
    public TerrainMeshIndirectBuffer indirectCommands() {
        return indirectCommands;
    }

    private static ResolvedBufferResource meshDataDescriptor(int elementCount, long strideBytes) {
        long normalizedStride = Math.max(1L, strideBytes);
        long normalizedElements = Math.max(1L, elementCount);
        return new ResolvedBufferResource(
                TERRAIN_MESH_DATA_BUFFER_ID,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                normalizedElements,
                normalizedStride,
                normalizedElements * normalizedStride);
    }

    private static ResolvedBufferResource regionIndexDescriptor(int elementCount) {
        long normalizedElements = Math.max(1L, elementCount);
        return new ResolvedBufferResource(
                TERRAIN_REGION_INDEX_BUFFER_ID,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                normalizedElements,
                REGION_INDEX_STRIDE_BYTES,
                normalizedElements * REGION_INDEX_STRIDE_BYTES);
    }

    private static ResolvedBufferResource indirectCommandDescriptor(long commandCapacity) {
        long normalizedCapacity = Math.max(1L, commandCapacity);
        return new ResolvedBufferResource(
                TERRAIN_INDIRECT_COMMAND_BUFFER_ID,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                normalizedCapacity,
                BackendStorageBufferCommandStride.INDIRECT_COMMAND_BYTES,
                normalizedCapacity * BackendStorageBufferCommandStride.INDIRECT_COMMAND_BYTES);
    }

    private static ResolvedBufferResource counterDescriptor(KeyId resourceId, int elementCount) {
        long normalizedElements = Math.max(1L, elementCount);
        return new ResolvedBufferResource(
                resourceId,
                BufferRole.ATOMIC_COUNTER,
                BufferUpdatePolicy.DYNAMIC,
                normalizedElements,
                COUNTER_STRIDE_BYTES,
                normalizedElements * COUNTER_STRIDE_BYTES);
    }

    private static ResolvedBufferResource readbackDescriptor(int elementCount) {
        long normalizedElements = Math.max(1L, elementCount);
        return new ResolvedBufferResource(
                TERRAIN_MAX_ELEMENT_READBACK_BUFFER_ID,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                normalizedElements,
                COUNTER_STRIDE_BYTES,
                normalizedElements * COUNTER_STRIDE_BYTES);
    }

    private static int requiredMeshElementCount(int regionCapacity) {
        return Math.max(1, regionCapacity) * PASS_SIZE * SECTION_COUNT;
    }

    private static void disposeQuietly(@Nullable Object resource) {
        if (resource instanceof rogo.sketch.core.api.ResourceObject resourceObject && !resourceObject.isDisposed()) {
            resourceObject.dispose();
        }
    }

    private static final class BackendStorageBufferCommandStride {
        private static final long INDIRECT_COMMAND_BYTES = 20L;
    }
}
