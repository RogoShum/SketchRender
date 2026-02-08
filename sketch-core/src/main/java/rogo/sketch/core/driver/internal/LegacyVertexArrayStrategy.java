package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

/**
 * Legacy implementation of vertex array operations.
 * Uses traditional bind-then-operate pattern with state save/restore.
 */
public class LegacyVertexArrayStrategy implements IGLVertexArrayStrategy {
    
    @Override
    public int createVertexArray() {
        return GL30.glGenVertexArrays();
    }
    
    @Override
    public void deleteVertexArray(int id) {
        GL30.glDeleteVertexArrays(id);
    }
    
    @Override
    public void bindVertexArray(int id) {
        GL30.glBindVertexArray(id);
    }

    @Override
    public void bindVertexBuffer(int id) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
    }

    @Override
    public void enableVertexAttribArray(int vao, int index) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL20.glEnableVertexAttribArray(index);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void disableVertexAttribArray(int vao, int index) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL20.glDisableVertexAttribArray(index);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void vertexAttribFormat(int vao, int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        // GL 4.3 feature, use with caution in legacy mode
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL43.glVertexAttribFormat(attribIndex, size, type, normalized, relativeOffset);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void vertexAttribIFormat(int vao, int attribIndex, int size, int type, int relativeOffset) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL43.glVertexAttribIFormat(attribIndex, size, type, relativeOffset);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void vertexAttribLFormat(int vao, int attribIndex, int size, int type, int relativeOffset) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL43.glVertexAttribLFormat(attribIndex, size, type, relativeOffset);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void vertexAttribBinding(int vao, int attribIndex, int bindingIndex) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL43.glVertexAttribBinding(attribIndex, bindingIndex);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void vertexBuffer(int vao, int bindingIndex, int buffer, long offset, int stride) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL43.glBindVertexBuffer(bindingIndex, buffer, offset, stride);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void elementBuffer(int vao, int buffer) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void vertexBindingDivisor(int vao, int bindingIndex, int divisor) {
        int previousVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vao);
        GL43.glVertexBindingDivisor(bindingIndex, divisor);
        GL30.glBindVertexArray(previousVAO);
    }
    
    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        // Requires VAO to already be bound
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        // Requires VAO to already be bound
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void vertexAttribLPointer(int index, int size, int type, int stride, long pointer) {
        // Requires VAO to already be bound
        GL42.glVertexAttribLPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        // Requires VAO to already be bound
        GL33.glVertexAttribDivisor(index, divisor);
    }
}


