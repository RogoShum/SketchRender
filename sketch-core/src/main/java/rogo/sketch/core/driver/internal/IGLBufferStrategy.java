package rogo.sketch.core.driver.internal;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Strategy interface for OpenGL buffer operations.
 * Implementations provide either DSA (Direct State Access) or Legacy approaches.
 */
public interface IGLBufferStrategy {
    
    /**
     * Create a new buffer object
     * @return The buffer handle
     */
    int createBuffer();
    
    /**
     * Delete a buffer object
     * @param id The buffer handle
     */
    void deleteBuffer(int id);
    
    /**
     * Allocate and optionally initialize buffer storage
     * @param id The buffer handle
     * @param size Size in bytes
     * @param data Data pointer (can be 0 for uninitialized)
     * @param usage Usage hint (GL_STATIC_DRAW, GL_DYNAMIC_DRAW, etc.)
     */
    void bufferData(int id, long size, long data, int usage);
    
    /**
     * Allocate and optionally initialize buffer storage from ByteBuffer
     * @param id The buffer handle
     * @param data The data buffer
     * @param usage Usage hint
     */
    void bufferData(int id, ByteBuffer data, int usage);
    
    /**
     * Allocate and optionally initialize buffer storage from FloatBuffer
     * @param id The buffer handle
     * @param data The float data buffer
     * @param usage Usage hint
     */
    void bufferData(int id, FloatBuffer data, int usage);
    
    /**
     * Update a subset of buffer data
     * @param id The buffer handle
     * @param offset Offset in bytes
     * @param size Size in bytes
     * @param data Data pointer
     */
    void bufferSubData(int id, long offset, long size, long data);
    
    /**
     * Update a subset of buffer data from ByteBuffer
     * @param id The buffer handle
     * @param offset Offset in bytes
     * @param data The data buffer
     */
    void bufferSubData(int id, long offset, ByteBuffer data);
    
    /**
     * Map a buffer for read/write access
     * @param id The buffer handle
     * @param target Buffer target (for legacy binding)
     * @param access Access flags
     * @return ByteBuffer mapped to the buffer, or null on failure
     */
    ByteBuffer mapBuffer(int id, int target, int access);
    
    /**
     * Map a buffer range for read/write access
     * @param id The buffer handle
     * @param target Buffer target (for legacy binding)
     * @param offset Offset in bytes
     * @param length Length in bytes
     * @param access Access flags
     * @return ByteBuffer mapped to the buffer range, or null on failure
     */
    ByteBuffer mapBufferRange(int id, int target, long offset, long length, int access);
    
    /**
     * Unmap a previously mapped buffer
     * @param id The buffer handle
     * @param target Buffer target (for legacy binding)
     * @return true if successful
     */
    boolean unmapBuffer(int id, int target);
    
    /**
     * Bind a buffer to a specific target
     * @param target The buffer target (GL_ARRAY_BUFFER, GL_ELEMENT_ARRAY_BUFFER, etc.)
     * @param id The buffer handle
     */
    void bindBuffer(int target, int id);
    
    /**
     * Bind a buffer to an indexed buffer target (for SSBO, UBO, etc.)
     * @param target The indexed target (GL_SHADER_STORAGE_BUFFER, GL_UNIFORM_BUFFER, etc.)
     * @param index The binding point index
     * @param id The buffer handle
     */
    void bindBufferBase(int target, int index, int id);
    
    /**
     * Bind a range of a buffer to an indexed buffer target
     * @param target The indexed target
     * @param index The binding point index
     * @param id The buffer handle
     * @param offset Offset in bytes
     * @param size Size in bytes
     */
    void bindBufferRange(int target, int index, int id, long offset, long size);
    
    /**
     * Allocate immutable buffer storage (GL 4.4+)
     * @param id The buffer handle
     * @param size Size in bytes
     * @param data Data pointer (can be 0)
     * @param flags Storage flags (GL_MAP_READ_BIT, GL_MAP_WRITE_BIT, GL_MAP_PERSISTENT_BIT, etc.)
     */
    void bufferStorage(int id, long size, long data, int flags);
    
    /**
     * Copy data between buffers
     * @param readBuffer Source buffer handle
     * @param writeBuffer Destination buffer handle
     * @param readOffset Source offset in bytes
     * @param writeOffset Destination offset in bytes
     * @param size Size in bytes to copy
     */
    void copyBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size);
}


