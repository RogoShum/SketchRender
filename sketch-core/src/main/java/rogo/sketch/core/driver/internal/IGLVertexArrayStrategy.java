package rogo.sketch.core.driver.internal;

/**
 * Strategy interface for OpenGL vertex array operations.
 * Implementations provide either DSA (Direct State Access) or Legacy approaches.
 */
public interface IGLVertexArrayStrategy {
    
    /**
     * Create a new vertex array object
     * @return The VAO handle
     */
    int createVertexArray();
    
    /**
     * Delete a vertex array object
     * @param id The VAO handle
     */
    void deleteVertexArray(int id);
    
    /**
     * Bind a vertex array object
     * @param id The VAO handle (0 to unbind)
     */
    void bindVertexArray(int id);

    /**
     * Bind a vertex buffer object
     * @param id The VBO handle (0 to unbind)
     */
    void bindVertexBuffer(int id);
    
    /**
     * Enable a vertex attribute array
     * @param vao The VAO handle
     * @param index Attribute index
     */
    void enableVertexAttribArray(int vao, int index);
    
    /**
     * Disable a vertex attribute array
     * @param vao The VAO handle
     * @param index Attribute index
     */
    void disableVertexAttribArray(int vao, int index);
    
    /**
     * Set vertex attribute format (DSA style)
     * @param vao The VAO handle
     * @param attribIndex Attribute index
     * @param size Number of components (1, 2, 3, 4)
     * @param type Data type (GL_FLOAT, GL_INT, etc.)
     * @param normalized Whether to normalize integer values
     * @param relativeOffset Offset relative to binding point stride
     */
    void vertexAttribFormat(int vao, int attribIndex, int size, int type, boolean normalized, int relativeOffset);
    
    /**
     * Set integer vertex attribute format (DSA style)
     * @param vao The VAO handle
     * @param attribIndex Attribute index
     * @param size Number of components (1, 2, 3, 4)
     * @param type Data type (GL_INT, GL_UNSIGNED_INT, etc.)
     * @param relativeOffset Offset relative to binding point stride
     */
    void vertexAttribIFormat(int vao, int attribIndex, int size, int type, int relativeOffset);
    
    /**
     * Set double vertex attribute format (DSA style)
     * @param vao The VAO handle
     * @param attribIndex Attribute index
     * @param size Number of components (1, 2, 3, 4)
     * @param type Data type (GL_DOUBLE)
     * @param relativeOffset Offset relative to binding point stride
     */
    void vertexAttribLFormat(int vao, int attribIndex, int size, int type, int relativeOffset);
    
    /**
     * Bind an attribute to a binding point
     * @param vao The VAO handle
     * @param attribIndex Attribute index
     * @param bindingIndex Binding point index
     */
    void vertexAttribBinding(int vao, int attribIndex, int bindingIndex);
    
    /**
     * Set vertex buffer binding
     * @param vao The VAO handle
     * @param bindingIndex Binding point index
     * @param buffer VBO handle
     * @param offset Offset in bytes
     * @param stride Stride in bytes
     */
    void vertexBuffer(int vao, int bindingIndex, int buffer, long offset, int stride);
    
    /**
     * Set element buffer
     * @param vao The VAO handle
     * @param buffer EBO handle
     */
    void elementBuffer(int vao, int buffer);
    
    /**
     * Set binding divisor for instancing
     * @param vao The VAO handle
     * @param bindingIndex Binding point index
     * @param divisor Divisor (0 for per-vertex, 1 for per-instance)
     */
    void vertexBindingDivisor(int vao, int bindingIndex, int divisor);
    
    /**
     * Legacy: Set vertex attribute pointer
     * @param index Attribute index
     * @param size Number of components
     * @param type Data type
     * @param normalized Whether to normalize
     * @param stride Stride in bytes
     * @param pointer Offset in bytes (as long for VBO offset)
     */
    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer);
    
    /**
     * Legacy: Set integer vertex attribute pointer
     * @param index Attribute index
     * @param size Number of components
     * @param type Data type
     * @param stride Stride in bytes
     * @param pointer Offset in bytes
     */
    void vertexAttribIPointer(int index, int size, int type, int stride, long pointer);
    
    /**
     * Legacy: Set double vertex attribute pointer
     * @param index Attribute index
     * @param size Number of components
     * @param type Data type
     * @param stride Stride in bytes
     * @param pointer Offset in bytes
     */
    void vertexAttribLPointer(int index, int size, int type, int stride, long pointer);
    
    /**
     * Legacy: Set attribute divisor for instancing
     * @param index Attribute index
     * @param divisor Divisor
     */
    void vertexAttribDivisor(int index, int divisor);
}


