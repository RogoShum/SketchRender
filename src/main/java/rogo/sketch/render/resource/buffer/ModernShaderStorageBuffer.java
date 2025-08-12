package rogo.sketch.render.resource.buffer;

import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.Std430DataFormat;
import rogo.sketch.render.data.filler.SSBOFiller;
import rogo.sketch.render.data.filler.DataFiller;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.DataResourceObject;

/**
 * Modern SSBO implementation with integrated data format and filling capabilities
 * Supports both traditional vertex-like layouts and std430-compliant SSBO layouts
 */
public class ModernShaderStorageBuffer implements DataResourceObject {
    private final int id;
    private boolean isDisposed = false;
    private long bufferPointer;
    private long capacity;
    private long dataCount;
    private final DataFormat dataFormat;
    private final boolean isStd430;
    
    // Cache for data fillers
    private SSBOFiller cachedFiller;
    
    public ModernShaderStorageBuffer(DataFormat format, long dataCount, int usage) {
        this.dataFormat = format;
        this.isStd430 = format instanceof Std430DataFormat;
        this.dataCount = dataCount;
        
        // Validate std430 layout if applicable
        if (isStd430) {
            Std430DataFormat std430Format = (Std430DataFormat) format;
            if (!std430Format.validateStd430Layout()) {
                throw new IllegalArgumentException("Invalid std430 layout in provided format");
            }
        }
        
        // Calculate capacity based on format stride
        this.capacity = dataCount * format.getStride();
        if (this.capacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Buffer capacity too large");
        }
        
        // Allocate memory
        this.bufferPointer = MemoryUtil.nmemCalloc(dataCount, format.getStride());
        
        // Create OpenGL buffer
        this.id = GL15.glGenBuffers();
        if (id < 0) {
            throw new RuntimeException("Failed to create SSBO");
        }
        
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, this.capacity, bufferPointer, usage);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Create SSBO with std430-compliant format
     */
    public static ModernShaderStorageBuffer createStd430(Std430DataFormat format, long dataCount, int usage) {
        return new ModernShaderStorageBuffer(format, dataCount, usage);
    }
    
    /**
     * Create SSBO with traditional vertex-like format
     */
    public static ModernShaderStorageBuffer createTraditional(DataFormat format, long dataCount, int usage) {
        return new ModernShaderStorageBuffer(format, dataCount, usage);
    }
    
    /**
     * Get a data filler for this SSBO
     */
    public SSBOFiller getFiller() {
        if (cachedFiller == null) {
            // Create a wrapper that presents this ModernSSBO as a traditional SSBO
            SSBOWrapper wrapper = new SSBOWrapper(this);
            cachedFiller = new SSBOFiller(dataFormat, wrapper);
        }
        return cachedFiller;
    }
    
    /**
     * Get a data filler starting at specific data index
     */
    public DataFiller getFillerAt(long dataIndex) {
        SSBOFiller filler = getFiller();
        return filler.vertex(dataIndex);
    }
    
    /**
     * Fill data using fluent API
     */
    public FluentFiller fill() {
        return new FluentFiller(getFiller());
    }
    
    /**
     * Fill data at specific index using fluent API
     */
    public FluentFiller fillAt(long dataIndex) {
        SSBOFiller filler = getFiller();
        filler.vertex(dataIndex);
        return new FluentFiller(filler);
    }
    
    // DataBufferObject implementation
    @Override
    public int getHandle() {
        return id;
    }
    
    @Override
    public long getDataCount() {
        return dataCount;
    }
    
    @Override
    public long getCapacity() {
        return capacity;
    }
    
    @Override
    public long getStride() {
        return dataFormat.getStride();
    }
    
    @Override
    public long getMemoryAddress() {
        return bufferPointer;
    }
    
    /**
     * Get the data format used by this SSBO
     */
    public DataFormat getDataFormat() {
        return dataFormat;
    }
    
    /**
     * Check if this SSBO uses std430 layout
     */
    public boolean isStd430() {
        return isStd430;
    }
    
    /**
     * Bind SSBO to shader slot
     */
    public void bindShaderSlot(int bindingPoint) {
        checkDisposed();
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
    }
    
    /**
     * Upload all data to GPU
     */
    public void upload() {
        checkDisposed();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, capacity, bufferPointer);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Upload specific data element to GPU
     */
    public void upload(long dataIndex) {
        checkDisposed();
        if (dataIndex >= dataCount) {
            throw new IndexOutOfBoundsException("Data index out of bounds: " + dataIndex);
        }
        
        long offset = dataIndex * getStride();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset, getStride(), bufferPointer + offset);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Upload range of data elements to GPU
     */
    public void uploadRange(long startIndex, long count) {
        checkDisposed();
        if (startIndex + count > dataCount) {
            throw new IndexOutOfBoundsException("Range out of bounds");
        }
        
        long offset = startIndex * getStride();
        long size = count * getStride();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset, size, bufferPointer + offset);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    /**
     * Ensure capacity for required number of data elements
     */
    public void ensureCapacity(long requiredDataCount, boolean preserveData) {
        checkDisposed();
        
        if (requiredDataCount <= dataCount) {
            return;
        }
        
        long newCapacity = requiredDataCount * getStride();
        long newBufferPointer = MemoryUtil.nmemCalloc(requiredDataCount, getStride());
        
        if (preserveData) {
            MemoryUtil.memCopy(bufferPointer, newBufferPointer, capacity);
        }
        
        MemoryUtil.nmemFree(bufferPointer);
        this.bufferPointer = newBufferPointer;
        this.capacity = newCapacity;
        this.dataCount = requiredDataCount;
        
        // Update GPU buffer
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        
        // Invalidate cached filler
        cachedFiller = null;
    }
    
    /**
     * Clear all data in the buffer
     */
    public void clear() {
        checkDisposed();
        MemoryUtil.memSet(bufferPointer, 0, capacity);
    }
    
    @Override
    public void dispose() {
        if (isDisposed) return;
        
        MemoryUtil.nmemFree(bufferPointer);
        GL15.glDeleteBuffers(id);
        isDisposed = true;
        cachedFiller = null;
    }
    
    private void checkDisposed() {
        if (isDisposed) {
            throw new IllegalStateException("SSBO has been disposed");
        }
    }
    
    /**
     * Fluent API for easy data filling
     */
    public static class FluentFiller {
        private final SSBOFiller filler;
        
        private FluentFiller(SSBOFiller filler) {
            this.filler = filler;
        }
        
        public FluentFiller at(long dataIndex) {
            filler.vertex(dataIndex);
            return this;
        }
        
        public FluentFiller element(int elementIndex) {
            filler.element(elementIndex);
            return this;
        }
        
        public FluentFiller floatValue(float value) {
            filler.floatValue(value);
            return this;
        }
        
        public FluentFiller intValue(int value) {
            filler.intValue(value);
            return this;
        }
        
        public FluentFiller vec2f(float x, float y) {
            filler.vec2f(x, y);
            return this;
        }
        
        public FluentFiller vec3f(float x, float y, float z) {
            filler.vec3f(x, y, z);
            return this;
        }
        
        public FluentFiller vec4f(float x, float y, float z, float w) {
            filler.vec4f(x, y, z, w);
            return this;
        }
        
        public FluentFiller upload() {
            filler.upload();
            return this;
        }
        
        public FluentFiller uploadAt(long index) {
            filler.uploadAt(index);
            return this;
        }
        
        public ModernShaderStorageBuffer getSSBO() {
            return (ModernShaderStorageBuffer) ((SSBOWrapper) filler.getSSBO()).getWrapped();
        }
    }
    
    /**
     * Wrapper to make ModernSSBO compatible with existing SSBOFiller
     */
    private static class SSBOWrapper extends ShaderStorageBuffer {
        private final ModernShaderStorageBuffer wrapped;
        
        public SSBOWrapper(ModernShaderStorageBuffer wrapped) {
            super(wrapped); // Use copy constructor
            this.wrapped = wrapped;
        }
        
        public ModernShaderStorageBuffer getWrapped() {
            return wrapped;
        }
        
        @Override
        public void upload() {
            wrapped.upload();
        }
        
        @Override
        public void upload(long index) {
            wrapped.upload(index);
        }
        
        @Override
        public void upload(long index, int stride) {
            // Convert stride-based upload to element-based
            long elementCount = stride / wrapped.getStride();
            wrapped.uploadRange(index, elementCount);
        }
    }
}
