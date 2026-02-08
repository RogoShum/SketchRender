package rogo.sketch.core.driver;

import rogo.sketch.core.driver.internal.*;
import rogo.sketch.core.state.snapshot.GLStateSnapshot;
import rogo.sketch.core.state.snapshot.SnapshotScope;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Abstract graphics API providing a unified interface for graphics operations.
 * Supports both DSA (Direct State Access) and Legacy OpenGL modes through strategy pattern.
 * Designed for future Vulkan compatibility.
 */
public abstract class GraphicsAPI {

    // ==================== Strategy Accessors ====================

    /**
     * Get the buffer strategy (DSA or Legacy)
     */
    public abstract IGLBufferStrategy getBufferStrategy();

    /**
     * Get the texture strategy (DSA or Legacy)
     */
    public abstract IGLTextureStrategy getTextureStrategy();

    /**
     * Get the shader strategy (DSA or Legacy)
     */
    public abstract IGLShaderStrategy getShaderStrategy();

    /**
     * Get the framebuffer strategy (DSA or Legacy)
     */
    public abstract IGLFramebufferStrategy getFramebufferStrategy();

    /**
     * Get the vertex array strategy (DSA or Legacy)
     */
    public abstract IGLVertexArrayStrategy getVertexArrayStrategy();

    // ==================== Render State Operations ====================

    // --- Blend State ---
    public abstract void enableBlend();

    public abstract void disableBlend();

    public abstract void blendFunc(int src, int dst);

    public abstract void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);

    public abstract void blendEquation(int mode);

    public abstract void blendEquationSeparate(int modeRGB, int modeAlpha);

    // --- Depth State ---
    public abstract void enableDepthTest();

    public abstract void disableDepthTest();

    public abstract void depthFunc(int func);

    public abstract void depthMask(boolean enable);

    public abstract void depthRange(double near, double far);

    // --- Cull State ---
    public abstract void enableCullFace();

    public abstract void disableCullFace();

    public abstract void cullFace(int face);

    public abstract void frontFace(int face);

    // --- Scissor State ---
    public abstract void enableScissor(int x, int y, int w, int h);

    public abstract void disableScissor();

    // --- Stencil State ---
    public abstract void enableStencil();

    public abstract void disableStencil();

    public abstract void stencilFunc(int func, int ref, int mask);

    public abstract void stencilOp(int fail, int zfail, int zpass);

    public abstract void stencilMask(int mask);

    // --- Polygon State ---
    public abstract void polygonMode(int face, int mode);

    public abstract void enablePolygonOffset();

    public abstract void disablePolygonOffset();

    public abstract void polygonOffset(float factor, float units);

    // --- Viewport ---
    public abstract void viewport(int x, int y, int w, int h);

    // --- Color Mask ---
    public abstract void colorMask(boolean red, boolean green, boolean blue, boolean alpha);

    // --- Logic Op ---
    public abstract void enableLogicOp();

    public abstract void disableLogicOp();

    public abstract void logicOp(int opcode);

    // --- Clear Operations ---
    public abstract void clear(int mask);

    public abstract void clearColor(float r, float g, float b, float a);

    public abstract void clearDepth(double depth);

    public abstract void clearStencil(int stencil);

    // ==================== Buffer Operations (Handle-based) ====================

    /**
     * Create a new buffer
     *
     * @return The buffer handle
     */
    public int createBuffer() {
        return getBufferStrategy().createBuffer();
    }

    /**
     * Delete a buffer
     *
     * @param id The buffer handle
     */
    public void deleteBuffer(int id) {
        getBufferStrategy().deleteBuffer(id);
    }

    /**
     * Upload data to a buffer (allocate + upload)
     *
     * @param id    Buffer handle
     * @param size  Size in bytes
     * @param data  Data pointer (native address)
     * @param usage Usage hint (GL_STATIC_DRAW, etc.)
     */
    public void uploadBuffer(int id, long size, long data, int usage) {
        getBufferStrategy().bufferData(id, size, data, usage);
    }

    /**
     * Upload data to a buffer from ByteBuffer
     *
     * @param id    Buffer handle
     * @param data  Data buffer
     * @param usage Usage hint
     */
    public void uploadBuffer(int id, ByteBuffer data, int usage) {
        getBufferStrategy().bufferData(id, data, usage);
    }

    /**
     * Update a subset of buffer data
     *
     * @param id     Buffer handle
     * @param offset Offset in bytes
     * @param size   Size in bytes
     * @param data   Data pointer
     */
    public void updateBuffer(int id, long offset, long size, long data) {
        getBufferStrategy().bufferSubData(id, offset, size, data);
    }

    /**
     * Update a subset of buffer data from ByteBuffer
     *
     * @param id     Buffer handle
     * @param offset Offset in bytes
     * @param data   Data buffer
     */
    public void updateBuffer(int id, long offset, ByteBuffer data) {
        getBufferStrategy().bufferSubData(id, offset, data);
    }

    /**
     * Map a buffer for read/write access
     *
     * @param id     Buffer handle
     * @param target Buffer target (for legacy)
     * @param access Access flags
     * @return Mapped ByteBuffer
     */
    public ByteBuffer mapBuffer(int id, int target, int access) {
        return getBufferStrategy().mapBuffer(id, target, access);
    }

    /**
     * Map a buffer range for read/write access
     *
     * @param id     Buffer handle
     * @param target Buffer target
     * @param offset Offset in bytes
     * @param length Length in bytes
     * @param access Access flags
     * @return Mapped ByteBuffer
     */
    public ByteBuffer mapBufferRange(int id, int target, long offset, long length, int access) {
        return getBufferStrategy().mapBufferRange(id, target, offset, length, access);
    }

    /**
     * Unmap a previously mapped buffer
     *
     * @param id     Buffer handle
     * @param target Buffer target
     * @return true if successful
     */
    public boolean unmapBuffer(int id, int target) {
        return getBufferStrategy().unmapBuffer(id, target);
    }

    /**
     * Bind a buffer to a target
     *
     * @param target Buffer target
     * @param id     Buffer handle
     */
    public void bindBuffer(int target, int id) {
        getBufferStrategy().bindBuffer(target, id);
    }

    /**
     * Bind a buffer to an indexed binding point (for SSBO, UBO)
     *
     * @param target Target (GL_SHADER_STORAGE_BUFFER, GL_UNIFORM_BUFFER)
     * @param index  Binding point index
     * @param id     Buffer handle
     */
    public void bindBufferBase(int target, int index, int id) {
        getBufferStrategy().bindBufferBase(target, index, id);
    }

    /**
     * Allocate immutable buffer storage
     *
     * @param id    Buffer handle
     * @param size  Size in bytes
     * @param data  Data pointer
     * @param flags Storage flags
     */
    public void bufferStorage(int id, long size, long data, int flags) {
        getBufferStrategy().bufferStorage(id, size, data, flags);
    }

    // ==================== Texture Operations (Handle-based) ====================

    /**
     * Create a new texture
     *
     * @param target Texture target (GL_TEXTURE_2D, etc.)
     * @return Texture handle
     */
    public int createTexture(int target) {
        return getTextureStrategy().createTexture(target);
    }

    /**
     * Create a new 2D texture
     *
     * @return Texture handle
     */
    public int genTextures() {
        return getTextureStrategy().createTexture(0x0DE1); // GL_TEXTURE_2D
    }

    /**
     * Delete a texture
     *
     * @param id Texture handle
     */
    public void deleteTextures(int id) {
        getTextureStrategy().deleteTexture(id);
    }

    /**
     * Upload 2D texture data
     *
     * @param id             Texture handle
     * @param level          Mipmap level
     * @param internalFormat Internal format
     * @param width          Width
     * @param height         Height
     * @param format         Pixel format
     * @param type           Pixel type
     * @param data           Pixel data
     */
    public void uploadTexture2D(int id, int level, int internalFormat, int width, int height,
                                int format, int type, ByteBuffer data) {
        getTextureStrategy().texImage2D(id, level, internalFormat, width, height, format, type, data);
    }

    /**
     * Set texture parameter (integer)
     *
     * @param id    Texture handle
     * @param pname Parameter name
     * @param param Parameter value
     */
    public void textureParameteri(int id, int pname, int param) {
        getTextureStrategy().texParameteri(id, pname, param);
    }

    /**
     * Generate mipmaps for a texture
     *
     * @param id Texture handle
     */
    public void generateTextureMipmap(int id) {
        getTextureStrategy().generateMipmap(id);
    }

    /**
     * Bind a texture to a texture unit
     *
     * @param unit Texture unit (0, 1, 2, ...)
     * @param id   Texture handle
     */
    public void bindTextureUnit(int unit, int id) {
        getTextureStrategy().bindTextureUnit(unit, id);
    }

    /**
     * Bind a texture as an image for compute shader access
     *
     * @param unit    Image unit
     * @param id      Texture handle
     * @param level   Mipmap level
     * @param layered Whether to bind all layers
     * @param layer   Layer index
     * @param access  Access mode
     * @param format  Image format
     */
    public void bindImageTexture(int unit, int id, int level, boolean layered, int layer, int access, int format) {
        getTextureStrategy().bindImageTexture(unit, id, level, layered, layer, access, format);
    }

    // Legacy compatibility methods
    public abstract void bindTexture(int target, int id);

    public abstract void activeTexture(int unit);

    /**
     * Legacy: Upload 2D texture data with target parameter
     *
     * @deprecated Use uploadTexture2D(id, ...) instead
     */
    @Deprecated
    public abstract void texImage2D(int target, int level, int internalFormat, int width, int height,
                                    int border, int format, int type, ByteBuffer data);

    /**
     * Legacy: Set texture parameter with target
     *
     * @deprecated Use textureParameteri(id, ...) instead
     */
    @Deprecated
    public abstract void texParameteri(int target, int pname, int param);

    /**
     * Legacy: Generate mipmaps with target
     *
     * @deprecated Use generateTextureMipmap(id) instead
     */
    @Deprecated
    public abstract void generateMipmap(int target);

    // ==================== Shader Operations ====================

    /**
     * Create a shader program
     *
     * @return Program handle
     */
    public int createProgram() {
        return getShaderStrategy().createProgram();
    }

    /**
     * Delete a shader program
     *
     * @param program Program handle
     */
    public void deleteProgram(int program) {
        getShaderStrategy().deleteProgram(program);
    }

    /**
     * Use/bind a shader program
     *
     * @param program Program handle (0 to unbind)
     */
    public void useProgram(int program) {
        getShaderStrategy().useProgram(program);
    }

    /**
     * Create a shader object
     *
     * @param type Shader type
     * @return Shader handle
     */
    public int createShader(int type) {
        return getShaderStrategy().createShader(type);
    }

    /**
     * Delete a shader object
     *
     * @param shader Shader handle
     */
    public void deleteShader(int shader) {
        getShaderStrategy().deleteShader(shader);
    }

    /**
     * Compile a shader
     *
     * @param shader Shader handle
     * @param source Source code
     */
    public void compileShader(int shader, String source) {
        getShaderStrategy().shaderSource(shader, source);
        getShaderStrategy().compileShader(shader);
    }

    /**
     * Attach shader to program
     *
     * @param program Program handle
     * @param shader  Shader handle
     */
    public void attachShader(int program, int shader) {
        getShaderStrategy().attachShader(program, shader);
    }

    /**
     * Link program
     *
     * @param program Program handle
     */
    public void linkProgram(int program) {
        getShaderStrategy().linkProgram(program);
    }

    /**
     * Set uniform value (uses strategy's DSA or legacy method)
     *
     * @param program  Program handle
     * @param location Uniform location
     * @param value    Integer value
     */
    public void uniform1i(int program, int location, int value) {
        getShaderStrategy().uniform1i(program, location, value);
    }

    /**
     * Set uniform value
     *
     * @param program  Program handle
     * @param location Uniform location
     * @param value    Float value
     */
    public void uniform1f(int program, int location, float value) {
        getShaderStrategy().uniform1f(program, location, value);
    }

    /**
     * Set uniform matrix
     *
     * @param program   Program handle
     * @param location  Uniform location
     * @param transpose Whether to transpose
     * @param values    Matrix values
     */
    public void uniformMatrix4fv(int program, int location, boolean transpose, FloatBuffer values) {
        getShaderStrategy().uniformMatrix4fv(program, location, transpose, values);
    }

    // ==================== Framebuffer Operations ====================

    /**
     * Create a framebuffer
     *
     * @return Framebuffer handle
     */
    public int createFramebuffer() {
        return getFramebufferStrategy().createFramebuffer();
    }

    /**
     * Delete a framebuffer
     *
     * @param id Framebuffer handle
     */
    public void deleteFramebuffer(int id) {
        getFramebufferStrategy().deleteFramebuffer(id);
    }

    /**
     * Bind a framebuffer
     *
     * @param target Framebuffer target
     * @param id     Framebuffer handle
     */
    public void bindFrameBuffer(int target, int id) {
        getFramebufferStrategy().bindFramebuffer(target, id);
    }

    /**
     * Bind framebuffer to default target (GL_FRAMEBUFFER)
     *
     * @param id Framebuffer handle
     */
    public void bindFrameBuffer(int id) {
        bindFrameBuffer(0x8D40, id); // GL_FRAMEBUFFER
    }

    /**
     * Attach a texture to a framebuffer
     *
     * @param framebuffer   Framebuffer handle
     * @param attachment    Attachment point
     * @param textureTarget Texture target
     * @param texture       Texture handle
     * @param level         Mipmap level
     */
    public void framebufferTexture2D(int framebuffer, int attachment, int textureTarget, int texture, int level) {
        getFramebufferStrategy().framebufferTexture2D(framebuffer, attachment, textureTarget, texture, level);
    }

    /**
     * Check framebuffer completeness
     *
     * @param framebuffer Framebuffer handle
     * @return Status code
     */
    public int checkFramebufferStatus(int framebuffer) {
        return getFramebufferStrategy().checkFramebufferStatus(framebuffer, 0x8D40); // GL_FRAMEBUFFER
    }

    // ==================== Vertex Array Operations ====================

    /**
     * Create a vertex array object
     *
     * @return VAO handle
     */
    public int createVertexArray() {
        return getVertexArrayStrategy().createVertexArray();
    }

    /**
     * Delete a vertex array object
     *
     * @param id VAO handle
     */
    public void deleteVertexArray(int id) {
        getVertexArrayStrategy().deleteVertexArray(id);
    }

    /**
     * Bind a vertex array object
     *
     * @param id VAO handle
     */
    public void bindVertexArray(int id) {
        getVertexArrayStrategy().bindVertexArray(id);
    }

    /**
     * Bind a vertex buffer object
     *
     * @param id VBO handle
     */
    public void bindVertexBuffer(int id) {
        getVertexArrayStrategy().bindVertexBuffer(id);
    }

    /**
     * Enable a vertex attribute array
     *
     * @param vao   VAO handle
     * @param index Attribute index
     */
    public void enableVertexAttribArray(int vao, int index) {
        getVertexArrayStrategy().enableVertexAttribArray(vao, index);
    }

    /**
     * Set vertex attribute format (DSA style)
     *
     * @param vao            VAO handle
     * @param attribIndex    Attribute index
     * @param size           Component count
     * @param type           Data type
     * @param normalized     Whether to normalize
     * @param relativeOffset Relative offset
     */
    public void vertexAttribFormat(int vao, int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        getVertexArrayStrategy().vertexAttribFormat(vao, attribIndex, size, type, normalized, relativeOffset);
    }

    /**
     * Bind attribute to binding point
     *
     * @param vao          VAO handle
     * @param attribIndex  Attribute index
     * @param bindingIndex Binding point
     */
    public void vertexAttribBinding(int vao, int attribIndex, int bindingIndex) {
        getVertexArrayStrategy().vertexAttribBinding(vao, attribIndex, bindingIndex);
    }

    /**
     * Set vertex buffer binding
     *
     * @param vao          VAO handle
     * @param bindingIndex Binding point
     * @param buffer       VBO handle
     * @param offset       Offset
     * @param stride       Stride
     */
    public void vertexArrayVertexBuffer(int vao, int bindingIndex, int buffer, long offset, int stride) {
        getVertexArrayStrategy().vertexBuffer(vao, bindingIndex, buffer, offset, stride);
    }

    /**
     * Set element buffer
     *
     * @param vao    VAO handle
     * @param buffer EBO handle
     */
    public void vertexArrayElementBuffer(int vao, int buffer) {
        getVertexArrayStrategy().elementBuffer(vao, buffer);
    }

    // ==================== Compute Shader Operations ====================

    /**
     * Dispatch compute shader
     *
     * @param numGroupsX Number of work groups in X
     * @param numGroupsY Number of work groups in Y
     * @param numGroupsZ Number of work groups in Z
     */
    public abstract void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ);

    /**
     * Issue a memory barrier
     *
     * @param barriers Barrier bits
     */
    public abstract void memoryBarrier(int barriers);

    // ==================== Draw Operations ====================

    /**
     * Draw arrays
     *
     * @param mode  Primitive mode
     * @param first First vertex
     * @param count Vertex count
     */
    public abstract void drawArrays(int mode, int first, int count);

    /**
     * Draw elements (indexed)
     *
     * @param mode    Primitive mode
     * @param count   Index count
     * @param type    Index type
     * @param indices Offset in bytes
     */
    public abstract void drawElements(int mode, int count, int type, long indices);

    /**
     * Draw elements instanced
     *
     * @param mode          Primitive mode
     * @param count         Index count
     * @param type          Index type
     * @param indices       Offset in bytes
     * @param instanceCount Number of instances
     */
    public abstract void drawElementsInstanced(int mode, int count, int type, long indices, int instanceCount);

    /**
     * Multi-draw indirect
     *
     * @param mode      Primitive mode
     * @param indirect  Offset in indirect buffer
     * @param drawCount Number of draw commands
     * @param stride    Stride between commands
     */
    public abstract void multiDrawElementsIndirect(int mode, int type, long indirect, int drawCount, int stride);

    // ==================== State Snapshot Operations ====================

    /**
     * Create a state snapshot for the given scope
     *
     * @param scope The scope of states to capture
     * @return The state snapshot
     */
    public abstract GLStateSnapshot snapshot(SnapshotScope scope);

    /**
     * Restore state from a snapshot
     *
     * @param snapshot The snapshot to restore
     */
    public abstract void restore(GLStateSnapshot snapshot);

    /**
     * Create a full state snapshot (captures all states)
     *
     * @return The state snapshot
     */
    public GLStateSnapshot snapshotFull() {
        return snapshot(SnapshotScope.full());
    }

    // ==================== Query Operations ====================

    /**
     * Get integer state value
     *
     * @param pname Parameter name
     * @return Parameter value
     */
    public abstract int getInteger(int pname);

    /**
     * Get boolean state value
     *
     * @param pname Parameter name
     * @return Parameter value
     */
    public abstract boolean getBoolean(int pname);

    /**
     * Get float state value
     *
     * @param pname Parameter name
     * @return Parameter value
     */
    public abstract float getFloat(int pname);

    /**
     * Check if a capability is enabled
     *
     * @param cap Capability
     * @return true if enabled
     */
    public abstract boolean isEnabled(int cap);

    // ==================== Utility Methods ====================

    /**
     * Check if this API uses DSA
     *
     * @return true if using DSA strategies
     */
    public abstract boolean usesDSA();

    /**
     * Get the API name
     *
     * @return API name (e.g., "OpenGL", "Minecraft")
     */
    public abstract String getAPIName();

    /**
     * Flush pending commands
     */
    public abstract void flush();

    /**
     * Finish all pending commands
     */
    public abstract void finish();
}
