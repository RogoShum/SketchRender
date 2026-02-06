package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * DSA (Direct State Access) implementation of buffer operations.
 * Uses OpenGL 4.5+ DSA functions for state-less buffer manipulation.
 */
public class DSABufferStrategy implements IGLBufferStrategy {
    
    @Override
    public int createBuffer() {
        return GL45.glCreateBuffers();
    }
    
    @Override
    public void deleteBuffer(int id) {
        GL15.glDeleteBuffers(id);
    }
    
    @Override
    public void bufferData(int id, long size, long data, int usage) {
        GL45.nglNamedBufferData(id, size, data, usage);
    }

    @Override
    public void bufferData(int target, int id, long size, long data, int usage) {
        GL45.nglNamedBufferData(id, size, data, usage);
    }
    
    @Override
    public void bufferData(int id, ByteBuffer data, int usage) {
        GL45.glNamedBufferData(id, data, usage);
    }
    
    @Override
    public void bufferData(int id, FloatBuffer data, int usage) {
        GL45.glNamedBufferData(id, data, usage);
    }
    
    @Override
    public void bufferSubData(int id, long offset, long size, long data) {
        GL45.nglNamedBufferSubData(id, offset, size, data);
    }

    @Override
    public void bufferSubData(int target, int id, long offset, long size, long data) {
        bufferSubData(id, size, data, GL15.GL_STREAM_DRAW);
    }
    
    @Override
    public void bufferSubData(int id, long offset, ByteBuffer data) {
        GL45.glNamedBufferSubData(id, offset, data);
    }
    
    @Override
    public ByteBuffer mapBuffer(int id, int target, int access) {
        // DSA ignores target parameter
        return GL45.glMapNamedBuffer(id, access);
    }
    
    @Override
    public ByteBuffer mapBufferRange(int id, int target, long offset, long length, int access) {
        // DSA ignores target parameter
        return GL45.glMapNamedBufferRange(id, offset, length, access);
    }
    
    @Override
    public boolean unmapBuffer(int id, int target) {
        // DSA ignores target parameter
        return GL45.glUnmapNamedBuffer(id);
    }
    
    @Override
    public void bindBuffer(int target, int id) {
        // Still need to bind for draw calls
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
        GL45.nglNamedBufferStorage(id, size, data, flags);
    }
    
    @Override
    public void copyBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        GL45.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
}


