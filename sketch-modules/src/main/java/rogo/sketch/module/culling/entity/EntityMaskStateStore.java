package rogo.sketch.module.culling.entity;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendBufferFactory;
import rogo.sketch.core.backend.BackendReadbackBuffer;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.BufferedResourceDescriptor;
import rogo.sketch.core.backend.BufferedResourceSet;
import rogo.sketch.core.backend.BufferingMode;
import rogo.sketch.core.backend.ResourceEpoch;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

/**
 * Core-owned entity culling resource store.
 */
public final class EntityMaskStateStore {
    private static final int ENTITY_RESULT_GROUP_SIZE = 64;
    private static final KeyId ENTITY_DATA_BUFFER_ID = KeyId.of("sketch_render", "entity_mask_data_buffer");
    private static final KeyId ENTITY_RESULT_CURRENT_BUFFER_ID = KeyId.of("sketch_render", "entity_mask_result_current");
    private static final KeyId ENTITY_RESULT_PREVIOUS_BUFFER_ID = KeyId.of("sketch_render", "entity_mask_result_previous");
    private static final KeyId ENTITY_RESULT_BUFFER_FAMILY_ID = KeyId.of("sketch_render", "entity_mask_result_family");

    private BackendStorageBuffer entityDataBuffer;
    private final BufferedResourceSet<BackendReadbackBuffer> resultBuffers = BufferedResourceSet.create(
            new BufferedResourceDescriptor(ENTITY_RESULT_BUFFER_FAMILY_ID, BufferingMode.DOUBLE_BUFFERED, 2, "entity-mask-results"),
            ignored -> null,
            EntityMaskStateStore::disposeQuietly);
    private ResourceEpoch resultEpoch = new ResourceEpoch(1L);

    public synchronized void ensureAllocated(int requiredSubjects) {
        if (!GraphicsDriver.isBootstrapped()) {
            return;
        }
        int normalizedSubjects = Math.max(EntityFeatureSchema.DEFAULT_INITIAL_CAPACITY, requiredSubjects);
        int resultByteCount = entityResultByteCount(normalizedSubjects);
        if (entityDataBuffer == null || entityDataBuffer.isDisposed()) {
            entityDataBuffer = BackendBufferFactory.createStorageBuffer(
                    ENTITY_DATA_BUFFER_ID,
                    entityDataDescriptor(normalizedSubjects),
                    null);
        } else {
            entityDataBuffer.ensureCapacity(normalizedSubjects, true);
        }

        boolean createdCurrent = false;
        boolean createdPrevious = false;
        BackendReadbackBuffer currentResultBuffer = resultBuffers.writeResource();
        BackendReadbackBuffer previousResultBuffer = resultBuffers.readResource();
        if (currentResultBuffer == null || currentResultBuffer.isDisposed()
                || previousResultBuffer == null || previousResultBuffer.isDisposed()) {
            resultBuffers.recreate(slot -> BackendBufferFactory.createReadbackBuffer(
                    slot == 0 ? ENTITY_RESULT_PREVIOUS_BUFFER_ID : ENTITY_RESULT_CURRENT_BUFFER_ID,
                    entityResultDescriptor(
                            slot == 0 ? ENTITY_RESULT_PREVIOUS_BUFFER_ID : ENTITY_RESULT_CURRENT_BUFFER_ID,
                            normalizedSubjects),
                    resultByteCount));
            currentResultBuffer = resultBuffers.writeResource();
            previousResultBuffer = resultBuffers.readResource();
            createdCurrent = true;
            createdPrevious = true;
        } else {
            currentResultBuffer.ensureCapacity(resultByteCount, false);
            previousResultBuffer.ensureCapacity(resultByteCount, false);
        }

        if (createdCurrent) {
            clearReadbackBuffer(currentResultBuffer);
        }
        if (createdPrevious) {
            clearReadbackBuffer(previousResultBuffer);
        }
    }

    public synchronized boolean isAllocated() {
        return entityDataBuffer != null
                && !entityDataBuffer.isDisposed()
                && resultBuffers.writeResource() != null
                && !resultBuffers.writeResource().isDisposed()
                && resultBuffers.readResource() != null
                && !resultBuffers.readResource().isDisposed();
    }

    public synchronized void refreshEntityInputs(EntitySourceRegistry registry) {
        if (registry == null) {
            return;
        }
        ensureAllocated(registry.subjectCount());
        if (!isAllocated() || entityDataBuffer.memoryAddress() == 0L) {
            return;
        }

        long baseAddress = entityDataBuffer.memoryAddress();
        long totalBytes = (long) registry.subjectCount() * EntityFeatureSchema.CULLING_V1_STRIDE_BYTES;
        if (totalBytes > 0L) {
            MemoryUtil.memSet(baseAddress, 0, totalBytes);
        }
        // Entity culling shader already performs frustum + Hi-Z evaluation.
        // Keeping CPU-side coarse visibility here causes host query divergence
        // with Minecraft's own shouldRender path, so entity upload stays full-set.
        registry.forEachIndexedOrdered((slot, data, visibilitySampled) -> {
            long byteOffset = (long) slot * EntityFeatureSchema.CULLING_V1_STRIDE_BYTES;
            MemoryUtil.memPutFloat(baseAddress + byteOffset, data.centerX());
            MemoryUtil.memPutFloat(baseAddress + byteOffset + 4L, data.centerY());
            MemoryUtil.memPutFloat(baseAddress + byteOffset + 8L, data.centerZ());
            MemoryUtil.memPutFloat(baseAddress + byteOffset + 12L, data.extentX());
            MemoryUtil.memPutFloat(baseAddress + byteOffset + 16L, data.extentY());
            MemoryUtil.memPutFloat(baseAddress + byteOffset + 20L, data.extentZ());
        });

        entityDataBuffer.position(totalBytes);
        if (totalBytes > 0L) {
            entityDataBuffer.upload();
            registry.markUploadSubmitted();
        }
        entityDataBuffer.position(0L);
    }

    public synchronized void swapBuffers(int logicTick, EntitySourceRegistry registry) {
        if (!isAllocated() || registry == null) {
            return;
        }
        resultBuffers.promote(resultEpoch);
        resultEpoch = resultEpoch.next();
        clearReadbackBuffer(resultBuffers.writeResource());
        registry.promotePendingSamples();
    }

    public synchronized boolean isVisible(Object subjectKey, EntitySourceRegistry registry) {
        if (subjectKey == null || registry == null || !isAllocated()) {
            return true;
        }
        int subjectIndex = registry.indexOf(subjectKey);
        if (subjectIndex < 0 || !registry.hasVisibilitySample(subjectKey)) {
            return true;
        }
        BackendReadbackBuffer currentResultBuffer = resultBuffers.writeResource();
        BackendReadbackBuffer previousResultBuffer = resultBuffers.readResource();
        if (currentResultBuffer != null
                && subjectIndex < currentResultBuffer.getDataCount()
                && currentResultBuffer.getUnsignedByte(subjectIndex) > 0) {
            return true;
        }
        return previousResultBuffer != null
                && subjectIndex < previousResultBuffer.getDataCount()
                && previousResultBuffer.getUnsignedByte(subjectIndex) > 0;
    }

    public synchronized int subjectCount(EntitySourceRegistry registry) {
        return registry != null ? registry.subjectCount() : 0;
    }

    public synchronized int dispatchGroupCount(EntitySourceRegistry registry) {
        int subjectCount = subjectCount(registry);
        if (subjectCount <= 0) {
            return 0;
        }
        return (subjectCount + 63) / 64;
    }

    public synchronized void dispose() {
        disposeQuietly(entityDataBuffer);
        resultBuffers.close();
        entityDataBuffer = null;
    }

    public synchronized @Nullable BackendStorageBuffer entityDataBuffer() {
        return entityDataBuffer;
    }

    public synchronized @Nullable BackendReadbackBuffer currentResultBuffer() {
        return resultBuffers.writeResource();
    }

    public synchronized @Nullable BackendReadbackBuffer previousResultBuffer() {
        return resultBuffers.readResource();
    }

    private static ResolvedBufferResource entityDataDescriptor(int subjectCount) {
        long normalizedSubjects = Math.max(1L, subjectCount);
        return new ResolvedBufferResource(
                ENTITY_DATA_BUFFER_ID,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                normalizedSubjects,
                EntityFeatureSchema.CULLING_V1_STRIDE_BYTES,
                normalizedSubjects * EntityFeatureSchema.CULLING_V1_STRIDE_BYTES);
    }

    private static ResolvedBufferResource entityResultDescriptor(KeyId resourceId, int subjectCount) {
        long resultByteCount = Math.max(1L, entityResultByteCount(subjectCount));
        return new ResolvedBufferResource(
                resourceId,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                resultByteCount,
                Byte.BYTES,
                resultByteCount);
    }

    private static int entityResultByteCount(int subjectCount) {
        int normalizedSubjects = Math.max(1, subjectCount);
        int groups = (normalizedSubjects + ENTITY_RESULT_GROUP_SIZE - 1) / ENTITY_RESULT_GROUP_SIZE;
        return groups * ENTITY_RESULT_GROUP_SIZE;
    }

    private static void clearReadbackBuffer(@Nullable BackendReadbackBuffer buffer) {
        if (buffer == null || buffer.isDisposed() || buffer.getMemoryAddress() == 0L || buffer.getCapacity() <= 0L) {
            return;
        }
        MemoryUtil.memSet(buffer.getMemoryAddress(), 0, buffer.getCapacity());
    }

    private static void disposeQuietly(@Nullable Object resource) {
        if (resource instanceof rogo.sketch.core.api.ResourceObject object && !object.isDisposed()) {
            object.dispose();
        }
    }
}
