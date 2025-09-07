package rogo.sketch.render.data.filler;

import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.resource.buffer.ShaderStorageBuffer;

/**
 * Data filler implementation for SSBO (Shader Storage Buffer Objects)
 * Focuses on GPU buffer operations and delegates data writing to internal fillers
 */
public class SSBOFiller extends DataFiller {
    private final ShaderStorageBuffer ssbo;
    private DataFiller internalFiller;

    public SSBOFiller(DataFormat format, ShaderStorageBuffer ssbo) {
        super(format);
        this.ssbo = ssbo;
        // Default to MemoryFiller for direct memory access
        useMemoryAccess();
    }

    /**
     * Switch to ByteBuffer mode for data filling
     */
    public SSBOFiller useByteBuffer() {
        this.internalFiller = ByteBufferFiller.create(format, getVertexCapacity());
        return this;
    }

    /**
     * Switch to direct memory access mode for data filling
     */
    public SSBOFiller useMemoryAccess() {
        this.internalFiller = MemoryFiller.wrap(format, ssbo.getMemoryAddress(), ssbo.getCapacity());
        return this;
    }

    // ===== Core data writing methods delegated to internal filler =====

    @Override
    public DataFiller putFloat(float value) {
        internalFiller.putFloat(value);
        return this;
    }

    @Override
    public DataFiller putInt(int value) {
        internalFiller.putInt(value);
        return this;
    }

    @Override
    public DataFiller putUInt(int value) {
        internalFiller.putUInt(value);
        return this;
    }

    @Override
    public DataFiller putByte(byte value) {
        internalFiller.putByte(value);
        return this;
    }

    @Override
    public DataFiller putUByte(byte value) {
        internalFiller.putUByte(value);
        return this;
    }

    @Override
    public DataFiller putShort(short value) {
        internalFiller.putShort(value);
        return this;
    }

    @Override
    public DataFiller putUShort(short value) {
        internalFiller.putUShort(value);
        return this;
    }

    @Override
    public DataFiller putDouble(double value) {
        internalFiller.putDouble(value);
        return this;
    }

    // ===== Random access methods =====

    @Override
    public void putFloatAt(long byteOffset, float value) {
        internalFiller.putFloatAt(byteOffset, value);
    }

    @Override
    public void putIntAt(long byteOffset, int value) {
        internalFiller.putIntAt(byteOffset, value);
    }

    @Override
    public void putUIntAt(long byteOffset, int value) {
        internalFiller.putUIntAt(byteOffset, value);
    }

    @Override
    public void putByteAt(long byteOffset, byte value) {
        internalFiller.putByteAt(byteOffset, value);
    }

    @Override
    public void putUByteAt(long byteOffset, byte value) {
        internalFiller.putUByteAt(byteOffset, value);
    }

    @Override
    public void putShortAt(long byteOffset, short value) {
        internalFiller.putShortAt(byteOffset, value);
    }

    @Override
    public void putUShortAt(long byteOffset, short value) {
        internalFiller.putUShortAt(byteOffset, value);
    }

    @Override
    public void putDoubleAt(long byteOffset, double value) {
        internalFiller.putDoubleAt(byteOffset, value);
    }

    @Override
    public boolean supportsRandomAccess() {
        return internalFiller.supportsRandomAccess();
    }

    // ===== SSBO-specific convenience methods =====

    /**
     * Set position at specific vertex index (assumes position is first 3 floats)
     */
    public SSBOFiller positionAt(long vertexIndex, float x, float y, float z) {
        long offset = vertexIndex * format.getStride();
        putFloatAt(offset, x);
        putFloatAt(offset + Float.BYTES, y);
        putFloatAt(offset + 2 * Float.BYTES, z);
        return this;
    }

    /**
     * Set color at specific vertex index and element offset
     */
    public SSBOFiller colorAt(long vertexIndex, int elementOffset, float r, float g, float b, float a) {
        long offset = vertexIndex * format.getStride() + elementOffset;
        putFloatAt(offset, r);
        putFloatAt(offset + Float.BYTES, g);
        putFloatAt(offset + 2 * Float.BYTES, b);
        putFloatAt(offset + 3 * Float.BYTES, a);
        return this;
    }


    // ===== GPU operations =====

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

    // ===== Capacity management =====

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
                useMemoryAccess(); // Recreate MemoryFiller with new address
            } else if (internalFiller instanceof ByteBufferFiller) {
                useByteBuffer(); // Recreate ByteBuffer wrapper
            }
        }

        return this;
    }

    /**
     * Get current vertex capacity
     */
    public int getVertexCapacity() {
        return (int) (ssbo.getCapacity() / format.getStride());
    }

    /**
     * Clear all data in the SSBO
     */
    public SSBOFiller clear() {
        if (internalFiller instanceof MemoryFiller) {
            ((MemoryFiller) internalFiller).clear();
        } else if (internalFiller instanceof DirectDataFiller) {
            ((DirectDataFiller) internalFiller).reset();
        }
        return this;
    }

    // ===== Getters =====

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
     * Get SSBO memory address
     */
    public long getMemoryAddress() {
        return ssbo.getMemoryAddress();
    }

    /**
     * Get SSBO capacity in bytes
     */
    public long getCapacity() {
        return ssbo.getCapacity();
    }

    // ===== Factory methods =====

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
    public void finish() {
        internalFiller.finish();
        // Automatically upload to GPU when finishing
        upload();
    }
}