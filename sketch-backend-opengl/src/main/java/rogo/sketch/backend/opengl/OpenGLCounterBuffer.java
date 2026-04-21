package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.memory.MemoryDomain;
import rogo.sketch.core.memory.MemoryLease;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshCounterBuffer;

import java.nio.ByteBuffer;

public class OpenGLCounterBuffer implements DataResourceObject, BackendInstalledBuffer, BackendCounterBuffer,
        TerrainMeshCounterBuffer {
    private final boolean persistent;
    private long bufferPointer;
    private final int bufferId;
    private long capacity;
    private long counterCount;
    private boolean disposed = false;
    private final ValueType counterType;
    private final MemoryLease cpuLease;

    private ByteBuffer mappedBuffer;

    private OpenGLCounterBuffer(ValueType type, boolean persistent) {
        this.persistent = persistent;
        counterType = type;
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, type.getStride());
        counterCount = 1;
        MemoryUtil.memSet(bufferPointer, 0, type.getStride());
        this.capacity = type.getStride();
        this.cpuLease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.CPU_NATIVE, "gl-counter-buffer/" + System.identityHashCode(this))
                .bindSuppliers(this::trackedReservedBytes, this::trackedLiveBytes);

        bufferId = GL33.glGenBuffers();

        if (persistent) {
            GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId);
            GL45.nglBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, 4, bufferPointer,
                    GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT);

            mappedBuffer = GL45.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,
                    0,
                    4,
                    GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT
            );
            GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        } else {
            GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId);
            GL15C.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
            GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        }
    }

    public OpenGLCounterBuffer(ValueType type) {
        this(type, false);
    }

    public static OpenGLCounterBuffer makePersistent(ValueType type) {
        return new OpenGLCounterBuffer(type, true);
    }

    public void resize(int count) {
        if (persistent) {
            throw new RuntimeException("not support yet");
        }

        this.capacity = getStride() * count;
        counterCount = count;
        MemoryUtil.nmemFree(bufferPointer);
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, this.capacity);
        MemoryUtil.memSet(bufferPointer, 0, this.capacity);
        GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId);
        GL15C.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getHandle() {
        return bufferId;
    }

    @Override
    public int handle() {
        return bufferId;
    }

    @Override
    public long getDataCount() {
        return counterCount;
    }

    public long getCapacity() {
        return capacity;
    }

    @Override
    public long getStride() {
        return counterType.getStride();
    }

    public long getMemoryAddress() {
        return bufferPointer;
    }

    public void bind() {
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
    }

    public void bindShaderSlot(int bindingPoint) {
        GL43.glBindBufferBase(GL43.GL_ATOMIC_COUNTER_BUFFER, bindingPoint, bufferId);
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.STORAGE_BUFFER.equals(normalizedType)) {
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, bufferId);
            return;
        }
        bindShaderSlot(binding);
    }

    @Override
    public long counterCount() {
        return counterCount;
    }

    @Override
    public long strideBytes() {
        return getStride();
    }

    public void updateCount(int count) {
        if (persistent) {
            throw new RuntimeException("not support yet");
        }

        MemoryUtil.memSet(bufferPointer, count, this.capacity);
        GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId);
        GL15C.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, capacity, bufferPointer);
        GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void dispose() {
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        GL43.glBindBuffer(GL43.GL_ATOMIC_COUNTER_BUFFER, 0);
        if (mappedBuffer != null) {
            GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId);
            GL45.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
            GL33.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            mappedBuffer = null;
        }
        GL33.glDeleteBuffers(bufferId);
        cpuLease.close();
        MemoryUtil.nmemFree(bufferPointer);

        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public ByteBuffer getMappedBuffer() {
        if (!persistent) {
            throw new RuntimeException("not support yet");
        }

        return mappedBuffer;
    }

    public int getInt() {
        if (!persistent) {
            throw new RuntimeException("not support yet");
        }

        if (mappedBuffer == null) {
            throw new IllegalStateException("Buffer is not mapped");
        }
        return mappedBuffer.asIntBuffer().get(0);
    }

    private long trackedReservedBytes() {
        return disposed ? 0L : capacity;
    }

    private long trackedLiveBytes() {
        return disposed ? 0L : capacity;
    }
}

