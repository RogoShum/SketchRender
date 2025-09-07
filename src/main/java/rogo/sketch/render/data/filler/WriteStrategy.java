package rogo.sketch.render.data.filler;

/**
 * Strategy interface for different data writing backends
 * Provides abstraction over ByteBuffer, direct memory access, etc.
 */
public interface WriteStrategy {
    
    // Basic type writing
    void writeFloat(float value);
    void writeInt(int value);
    void writeByte(byte value);
    void writeShort(short value);
    void writeDouble(double value);
    
    // Random access writing (optional - may throw UnsupportedOperationException)
    void writeFloatAt(long byteOffset, float value);
    void writeIntAt(long byteOffset, int value);
    void writeByteAt(long byteOffset, byte value);
    void writeShortAt(long byteOffset, short value);
    void writeDoubleAt(long byteOffset, double value);
    
    // Position management
    long getCurrentPosition();
    void setPosition(long position);
    void advance(int bytes);
    
    // Resource management
    void finish();
    void reset();
    
    // Capability queries
    boolean supportsRandomAccess();
    boolean supportsPositionTracking();
    long getCapacity();
}
