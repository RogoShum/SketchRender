package rogo.sketch.render.resource.buffer;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.DataBufferObject;

import java.nio.ByteBuffer;

public class CounterBuffer implements DataBufferObject {
    private final boolean persistent;
    private long bufferPointer;
    private final int bufferId;
    private long capacity;
    private long counterCount;
    private final VertexFormatElement.Type counterType;

    private ByteBuffer mappedBuffer;

    private CounterBuffer(VertexFormatElement.Type type, boolean persistent) {
        this.persistent = persistent;
        counterType = type;
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, type.getSize());
        counterCount = 1;
        MemoryUtil.memSet(bufferPointer, 0, type.getSize());
        this.capacity = type.getSize();

        bufferId = GL33.glGenBuffers();

        if (persistent) {
            GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
            GL45.nglBufferStorage(GL46.GL_PARAMETER_BUFFER, 4, bufferPointer,
                    GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT);

            mappedBuffer = GL45.glMapBufferRange(GL46.GL_PARAMETER_BUFFER,
                    0,
                    4,
                    GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT
            );
            GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
        } else {
            GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
            GL15C.nglBufferData(GL46.GL_PARAMETER_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
            GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
        }
    }

    public CounterBuffer(VertexFormatElement.Type type) {
        this(type, false);
    }

    public static CounterBuffer makePersistent(VertexFormatElement.Type type) {
        return new CounterBuffer(type, true);
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
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
        GL15C.nglBufferData(GL46.GL_PARAMETER_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
    }

    public int getHandle() {
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
        return counterType.getSize();
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

    public void updateCount(int count) {
        if (persistent) {
            throw new RuntimeException("not support yet");
        }

        MemoryUtil.memSet(bufferPointer, count, this.capacity);
        bind();
        GL15C.nglBufferSubData(GL46.GL_PARAMETER_BUFFER, 0, capacity, bufferPointer);
    }

    public void dispose() {
        GL33.glDeleteBuffers(bufferId);
        MemoryUtil.nmemFree(bufferPointer);

        if (mappedBuffer != null) {
            GL45.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
            mappedBuffer = null;
        }
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
}