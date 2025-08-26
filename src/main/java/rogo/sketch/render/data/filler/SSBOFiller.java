package rogo.sketch.render.data.filler;

import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.render.data.format.DataFormat;

/**
 * Data filler implementation for SSBO (Shader Storage Buffer Objects)
 * Supports both sequential and random access data filling
 */
public class SSBOFiller extends DataFiller {
    private final ShaderStorageBuffer ssbo;
    private DataFiller internalFiller;

    public SSBOFiller(DataFormat format, ShaderStorageBuffer ssbo) {
        super(format);
        this.ssbo = ssbo;
        // Use MemoryFiller for direct memory access
        this.internalFiller = MemoryFiller.wrap(format, ssbo.getMemoryAddress(), ssbo.getCapacity());
    }

    /**
     * Switch to ByteBuffer mode for data filling
     */
    public SSBOFiller useByteBuffer() {
        // Create a ByteBuffer wrapper around the SSBO memory
        java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memByteBuffer(
            ssbo.getMemoryAddress(), (int) ssbo.getCapacity());
        this.internalFiller = ByteBufferFiller.wrap(format, buffer);
        return this;
    }

    /**
     * Switch to direct memory access mode for data filling
     */
    public SSBOFiller useMemoryAccess() {
        this.internalFiller = MemoryFiller.wrap(format, ssbo.getMemoryAddress(), ssbo.getCapacity());
        return this;
    }

    @Override
    public DataFiller vertex(long index) {
        super.vertex(index);
        internalFiller.vertex(index);
        return this;
    }

    @Override
    public DataFiller element(int elementIndex) {
        super.element(elementIndex);
        internalFiller.element(elementIndex);
        return this;
    }

    @Override
    public void writeFloat(float value) {
        internalFiller.writeFloat(value);
    }

    @Override
    public void writeInt(int value) {
        internalFiller.writeInt(value);
    }

    @Override
    public void writeUInt(int value) {
        internalFiller.writeUInt(value);
    }

    @Override
    public void writeByte(byte value) {
        internalFiller.writeByte(value);
    }

    @Override
    public void writeUByte(byte value) {
        internalFiller.writeUByte(value);
    }

    @Override
    public void writeShort(short value) {
        internalFiller.writeShort(value);
    }

    @Override
    public void writeUShort(short value) {
        internalFiller.writeUShort(value);
    }

    @Override
    public void writeDouble(double value) {
        internalFiller.writeDouble(value);
    }

    /**
     * Upload data to GPU at a specific index
     */
    public SSBOFiller uploadAt(long index) {
        ssbo.upload(index);
        return this;
    }

    /**
     * Upload data to GPU at a specific index with custom stride
     */
    public SSBOFiller uploadAt(long index, int stride) {
        ssbo.upload(index, stride);
        return this;
    }

    /**
     * Upload all data to GPU
     */
    public SSBOFiller upload() {
        ssbo.upload();
        return this;
    }

    /**
     * Bind the SSBO to a shader slot
     */
    public SSBOFiller bind(int bindingPoint) {
        ssbo.bind(ResourceTypes.SHADER_STORAGE_BUFFER, bindingPoint);
        return this;
    }

    /**
     * Get the underlying SSBO
     */
    public ShaderStorageBuffer getSSBO() {
        return ssbo;
    }

    /**
     * Get the internal data filler
     */
    public DataFiller getInternalFiller() {
        return internalFiller;
    }

    /**
     * Ensure the SSBO has enough capacity for the required number of vertices
     */
    public SSBOFiller ensureCapacity(int vertexCount, boolean preserveData) {
        long requiredCapacity = (long) vertexCount * format.getStride();
        long currentCapacity = ssbo.getCapacity();
        
        if (requiredCapacity > currentCapacity) {
            // Calculate new capacity (grow by 50% or to required size, whichever is larger)
            long newCapacity = Math.max(requiredCapacity, currentCapacity + currentCapacity / 2);
            int newVertexCount = (int) (newCapacity / format.getStride());
            
            ssbo.ensureCapacity(newVertexCount, preserveData);
            
            // Update internal filler to use new memory address
            if (internalFiller instanceof MemoryFiller) {
                this.internalFiller = MemoryFiller.wrap(format, ssbo.getMemoryAddress(), ssbo.getCapacity());
            } else if (internalFiller instanceof ByteBufferFiller) {
                useByteBuffer(); // Recreate ByteBuffer wrapper
            }
        }
        
        return this;
    }

    /**
     * Clear all data in the SSBO
     */
    public SSBOFiller clear() {
        if (internalFiller instanceof MemoryFiller) {
            ((MemoryFiller) internalFiller).clear();
        } else if (internalFiller instanceof ByteBufferFiller) {
            ((ByteBufferFiller) internalFiller).reset();
        }
        currentVertex = 0;
        currentElementIndex = 0;
        return this;
    }

    /**
     * Create a new SSBOFiller with the specified format and vertex capacity
     */
    public static SSBOFiller create(DataFormat format, int vertexCapacity) {
        long stride = format.getStride();
        ShaderStorageBuffer ssbo = new ShaderStorageBuffer(vertexCapacity, stride, org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW);
        return new SSBOFiller(format, ssbo);
    }

    /**
     * Create a new SSBOFiller wrapping an existing SSBO
     */
    public static SSBOFiller wrap(DataFormat format, ShaderStorageBuffer ssbo) {
        return new SSBOFiller(format, ssbo);
    }
    
    @Override
    public void end() {
        // For SSBOFiller, upload data to GPU
        ssbo.upload();
    }
}