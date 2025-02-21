package rogo.sketchrender.shader.uniform;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.BufferObject;

import java.nio.ByteBuffer;

public class CountBuffer implements BufferObject {
    private final boolean persistent;
    private long bufferPointer;
    private final int bufferId;
    private long size;
    private long counterCount;
    private final VertexFormatElement.Type counterType;

    private ByteBuffer mappedBuffer;

    private CountBuffer(VertexFormatElement.Type type, boolean persistent) {
        this.persistent = persistent;
        counterType = type;
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, type.getSize());
        counterCount = 1;
        MemoryUtil.memSet(bufferPointer, 0, type.getSize());
        this.size = type.getSize();

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
            GL15C.nglBufferData(GL46.GL_PARAMETER_BUFFER, size, bufferPointer, GL15.GL_DYNAMIC_DRAW);
            GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
        }
    }

    public CountBuffer(VertexFormatElement.Type type) {
        this(type, false);
    }

    public static CountBuffer makePersistent(VertexFormatElement.Type type) {
        return new CountBuffer(type, true);
    }

    public void resize(int count) {
        if (persistent) {
            throw new RuntimeException("not support yet");
        }

        this.size = getStride() * count;
        counterCount = count;
        MemoryUtil.nmemFree(bufferPointer);
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, this.size);
        MemoryUtil.memSet(bufferPointer, 0, this.size);
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
        GL15C.nglBufferData(GL46.GL_PARAMETER_BUFFER, size, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
    }

    public int getId() {
        return bufferId;
    }

    @Override
    public long getDataNum() {
        return counterCount;
    }

    public long getSize() {
        return size;
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

    public void updateCount(int count) {
        if (persistent) {
            throw new RuntimeException("not support yet");
        }

        MemoryUtil.memSet(bufferPointer, count, this.size);
        bind();
        GL15C.nglBufferSubData(GL46.GL_PARAMETER_BUFFER, 0, size, bufferPointer);
    }

    public void cleanup() {
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