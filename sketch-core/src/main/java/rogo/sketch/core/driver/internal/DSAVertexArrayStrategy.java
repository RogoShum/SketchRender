package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.*;

/**
 * DSA (Direct State Access) implementation of vertex array operations.
 * Uses OpenGL 4.5+ DSA functions for state-less VAO manipulation.
 */
public class DSAVertexArrayStrategy implements IGLVertexArrayStrategy {
    
    @Override
    public int createVertexArray() {
        return GL45.glCreateVertexArrays();
    }
    
    @Override
    public void deleteVertexArray(int id) {
        GL30.glDeleteVertexArrays(id);
    }
    
    @Override
    public void bindVertexArray(int id) {
        // Binding is still required for draw operations
        GL30.glBindVertexArray(id);
    }

    @Override
    public void bindVertexBuffer(int id) {
        //GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
    }

    @Override
    public void enableVertexAttribArray(int vao, int index) {
        GL45.glEnableVertexArrayAttrib(vao, index);
    }
    
    @Override
    public void disableVertexAttribArray(int vao, int index) {
        GL45.glDisableVertexArrayAttrib(vao, index);
    }
    
    @Override
    public void vertexAttribFormat(int vao, int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        GL45.glVertexArrayAttribFormat(vao, attribIndex, size, type, normalized, relativeOffset);
    }
    
    @Override
    public void vertexAttribIFormat(int vao, int attribIndex, int size, int type, int relativeOffset) {
        GL45.glVertexArrayAttribIFormat(vao, attribIndex, size, type, relativeOffset);
    }
    
    @Override
    public void vertexAttribLFormat(int vao, int attribIndex, int size, int type, int relativeOffset) {
        GL45.glVertexArrayAttribLFormat(vao, attribIndex, size, type, relativeOffset);
    }
    
    @Override
    public void vertexAttribBinding(int vao, int attribIndex, int bindingIndex) {
        GL45.glVertexArrayAttribBinding(vao, attribIndex, bindingIndex);
    }
    
    @Override
    public void vertexBuffer(int vao, int bindingIndex, int buffer, long offset, int stride) {
        GL45.glVertexArrayVertexBuffer(vao, bindingIndex, buffer, offset, stride);
    }
    
    @Override
    public void elementBuffer(int vao, int buffer) {
        GL45.glVertexArrayElementBuffer(vao, buffer);
    }
    
    @Override
    public void vertexBindingDivisor(int vao, int bindingIndex, int divisor) {
        GL45.glVertexArrayBindingDivisor(vao, bindingIndex, divisor);
    }
    
    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        // Legacy method - requires VAO to be bound
        GL30.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        // Legacy method - requires VAO to be bound
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void vertexAttribLPointer(int index, int size, int type, int stride, long pointer) {
        // Legacy method - requires VAO to be bound
        GL42.glVertexAttribLPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        // Legacy method - requires VAO to be bound
        GL33.glVertexAttribDivisor(index, divisor);
    }
}


