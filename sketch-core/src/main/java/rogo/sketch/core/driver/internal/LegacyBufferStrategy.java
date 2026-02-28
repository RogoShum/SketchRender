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
        int previousBinding = GL11.glGetInteger(GL42.GL_COPY_WRITE_BUFFER_BINDING);
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.nglBufferData(DEFAULT_TARGET, size, data, usage);
        GL15.glBindBuffer(DEFAULT_TARGET, previousBinding);
    }
    
    @Override
    public void bufferData(int id, ByteBuffer data, int usage) {
        int previousBinding = GL11.glGetInteger(GL42.GL_COPY_WRITE_BUFFER_BINDING);
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.glBufferData(DEFAULT_TARGET, data, usage);
        GL15.glBindBuffer(DEFAULT_TARGET, previousBinding);
    }
    
    @Override
    public void bufferData(int id, FloatBuffer data, int usage) {
        int previousBinding = GL11.glGetInteger(GL42.GL_COPY_WRITE_BUFFER_BINDING);
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.glBufferData(DEFAULT_TARGET, data, usage);
        GL15.glBindBuffer(DEFAULT_TARGET, previousBinding);
    }
    
    @Override
    public void bufferSubData(int id, long offset, long size, long data) {
        int previousBinding = GL11.glGetInteger(GL42.GL_COPY_WRITE_BUFFER_BINDING);
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.nglBufferSubData(DEFAULT_TARGET, offset, size, data);
        GL15.glBindBuffer(DEFAULT_TARGET, previousBinding);
    }
    
    @Override
    public void bufferSubData(int id, long offset, ByteBuffer data) {
        int previousBinding = GL11.glGetInteger(GL42.GL_COPY_WRITE_BUFFER_BINDING);
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL15.glBufferSubData(DEFAULT_TARGET, offset, data);
        GL15.glBindBuffer(DEFAULT_TARGET, previousBinding);
    }
    
    @Override
    public ByteBuffer mapBuffer(int id, int target, int access) {
        int bindingQuery = getBindingQuery(target);
        int previousBinding = GL11.glGetInteger(bindingQuery);
        GL15.glBindBuffer(target, id);
        ByteBuffer result = GL15.glMapBuffer(target, access);
        // Note: Cannot restore binding while buffer is mapped
        return result;
    }
    
    @Override
    public ByteBuffer mapBufferRange(int id, int target, long offset, long length, int access) {
        int bindingQuery = getBindingQuery(target);
        int previousBinding = GL11.glGetInteger(bindingQuery);
        GL15.glBindBuffer(target, id);
        ByteBuffer result = GL30.glMapBufferRange(target, offset, length, access);
        // Note: Cannot restore binding while buffer is mapped
        return result;
    }
    
    @Override
    public boolean unmapBuffer(int id, int target) {
        // Assume buffer is still bound from map operation
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
        int previousBinding = GL11.glGetInteger(GL42.GL_COPY_WRITE_BUFFER_BINDING);
        GL15.glBindBuffer(DEFAULT_TARGET, id);
        GL44.nglBufferStorage(DEFAULT_TARGET, size, data, flags);
        GL15.glBindBuffer(DEFAULT_TARGET, previousBinding);
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
    
    /**
     * Get the query constant for a buffer target's current binding
     */
    private int getBindingQuery(int target) {
        return switch (target) {
            case GL15.GL_ARRAY_BUFFER -> GL15.GL_ARRAY_BUFFER_BINDING;
            case GL15.GL_ELEMENT_ARRAY_BUFFER -> GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING;
            case GL31.GL_UNIFORM_BUFFER -> GL31.GL_UNIFORM_BUFFER_BINDING;
            case GL43.GL_SHADER_STORAGE_BUFFER -> GL43.GL_SHADER_STORAGE_BUFFER_BINDING;
            case GL31.GL_COPY_READ_BUFFER -> GL42.GL_COPY_READ_BUFFER_BINDING;
            case GL31.GL_COPY_WRITE_BUFFER -> GL42.GL_COPY_WRITE_BUFFER_BINDING;
            case GL43.GL_DISPATCH_INDIRECT_BUFFER -> GL43.GL_DISPATCH_INDIRECT_BUFFER_BINDING;
            case GL43.GL_DRAW_INDIRECT_BUFFER -> GL43.GL_DRAW_INDIRECT_BUFFER_BINDING;
            default -> GL42.GL_COPY_WRITE_BUFFER_BINDING;
        };
    }
}


