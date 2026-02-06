package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Legacy implementation of buffer operations.
 * Uses traditional bind-then-operate pattern with state save/restore.
 */
public class LegacyBufferStrategy implements IGLBufferStrategy {

    // Default target for general buffer operations
    private static final int DEFAULT_TARGET = GL31.GL_COPY_WRITE_BUFFER;

    @Override
    public int createBuffer() {
        return GL15.glGenBuffers();
    }

    @Override
    public void deleteBuffer(int id) {
        GL15.glDeleteBuffers(id);
    }

    @Override
    public void bufferData(int id, long size, long data, int usage) {
        bufferData(DEFAULT_TARGET, id, size, data, usage);
    }

    @Override
    public void bufferData(int target, int id, long size, long data, int usage) {
        GL15.glBindBuffer(target, id);
        GL15.nglBufferData(target, size, data, usage);
        GL15.glBindBuffer(target, 0);
    }

    @Override
    public void bufferData(int id, ByteBuffer data, int usage) {
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.glBufferData(DEFAULT_TARGET, data, usage);
        GL15.glBindBuffer(DEFAULT_TARGET, 0);
    }

    @Override
    public void bufferData(int id, FloatBuffer data, int usage) {
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.glBufferData(DEFAULT_TARGET, data, usage);
        GL15.glBindBuffer(DEFAULT_TARGET, 0);
    }

    @Override
    public void bufferSubData(int id, long offset, long size, long data) {
        bufferSubData(DEFAULT_TARGET, id, offset, size, data);
    }

    @Override
    public void bufferSubData(int target, int id, long offset, long size, long data) {
        GL15.glBindBuffer(target, id);
        GL15.nglBufferSubData(target, offset, size, data);
        GL15.glBindBuffer(target, 0);
    }

    @Override
    public void bufferSubData(int id, long offset, ByteBuffer data) {
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.glBufferSubData(DEFAULT_TARGET, offset, data);
        GL15.glBindBuffer(DEFAULT_TARGET, 0);
    }

    @Override
    public ByteBuffer mapBuffer(int id, int target, int access) {
        GL15.glBindBuffer(target, id);
        return GL15.glMapBuffer(target, access);
    }

    @Override
    public ByteBuffer mapBufferRange(int id, int target, long offset, long length, int access) {
        GL15.glBindBuffer(target, id);
        return GL30.glMapBufferRange(target, offset, length, access);
    }

    @Override
    public boolean unmapBuffer(int id, int target) {
        return GL15.glUnmapBuffer(target);
    }

    @Override
    public void bindBuffer(int target, int id) {
        GL15.glBindBuffer(target, id);
    }

    @Override
    public void bindBufferBase(int target, int index, int id) {
        GL30.glBindBufferBase(target, index, id);
    }

    @Override
    public void bindBufferRange(int target, int index, int id, long offset, long size) {
        GL30.glBindBufferRange(target, index, id, offset, size);
    }

    @Override
    public void bufferStorage(int id, long size, long data, int flags) {
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL44.nglBufferStorage(DEFAULT_TARGET, size, data, flags);
        GL15.glBindBuffer(DEFAULT_TARGET, 0);
    }

    @Override
    public void copyBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        int prevRead = GL11.glGetInteger(GL42.GL_COPY_READ_BUFFER_BINDING);
        int prevWrite = GL11.glGetInteger(GL42.GL_COPY_WRITE_BUFFER_BINDING);

        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, readBuffer);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, writeBuffer);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, readOffset, writeOffset, size);

        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, prevRead);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, prevWrite);
    }
}