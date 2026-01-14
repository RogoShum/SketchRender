package rogo.sketch.render.data.builder;

import org.joml.*;
import rogo.sketch.render.data.format.DataFormat;

import java.nio.ByteBuffer;

/**
 * Interface for writing data to a buffer or memory address.
 * Supports GLSL data types and standard Java types.
 */
public interface DataBufferWriter {
    
    // ===== Core scalar data types =====
    
    DataBufferWriter putFloat(float value);
    DataBufferWriter putInt(int value);
    DataBufferWriter putUInt(int value);
    DataBufferWriter putByte(byte value);
    DataBufferWriter putUByte(byte value);
    DataBufferWriter putShort(short value);
    DataBufferWriter putUShort(short value);
    DataBufferWriter putDouble(double value);
    DataBufferWriter putLong(long value); // Added for completeness

    default DataBufferWriter putBool(boolean value) {
        return putByte((byte) (value ? 1 : 0));
    }

    // ===== GLSL float vector types =====

    default DataBufferWriter putVec2(float x, float y) {
        putFloat(x);
        putFloat(y);
        return this;
    }

    default DataBufferWriter putVec2(Vector2f vec) {
        return putVec2(vec.x, vec.y);
    }

    default DataBufferWriter putVec3(float x, float y, float z) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        return this;
    }

    default DataBufferWriter putVec3(Vector3f vec) {
        return putVec3(vec.x, vec.y, vec.z);
    }

    default DataBufferWriter putVec4(float x, float y, float z, float w) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        putFloat(w);
        return this;
    }

    default DataBufferWriter putVec4(Vector4f vec) {
        return putVec4(vec.x, vec.y, vec.z, vec.w);
    }

    // ===== GLSL integer vector types =====

    default DataBufferWriter putIVec2(int x, int y) {
        putInt(x);
        putInt(y);
        return this;
    }

    default DataBufferWriter putIVec2(Vector2i vec) {
        return putIVec2(vec.x, vec.y);
    }

    default DataBufferWriter putIVec3(int x, int y, int z) {
        putInt(x);
        putInt(y);
        putInt(z);
        return this;
    }

    default DataBufferWriter putIVec3(Vector3i vec) {
        return putIVec3(vec.x, vec.y, vec.z);
    }

    default DataBufferWriter putIVec4(int x, int y, int z, int w) {
        putInt(x);
        putInt(y);
        putInt(z);
        putInt(w);
        return this;
    }

    default DataBufferWriter putIVec4(Vector4i vec) {
        return putIVec4(vec.x, vec.y, vec.z, vec.w);
    }

    // ===== GLSL unsigned integer vector types =====

    default DataBufferWriter putUVec2(int x, int y) {
        putUInt(x);
        putUInt(y);
        return this;
    }

    default DataBufferWriter putUVec3(int x, int y, int z) {
        putUInt(x);
        putUInt(y);
        putUInt(z);
        return this;
    }

    default DataBufferWriter putUVec4(int x, int y, int z, int w) {
        putUInt(x);
        putUInt(y);
        putUInt(z);
        putUInt(w);
        return this;
    }

    // ===== Byte vector types =====

    default DataBufferWriter putVec2b(byte x, byte y) {
        putByte(x);
        putByte(y);
        return this;
    }

    default DataBufferWriter putVec3b(byte x, byte y, byte z) {
        putByte(x);
        putByte(y);
        putByte(z);
        return this;
    }

    default DataBufferWriter putVec4b(byte x, byte y, byte z, byte w) {
        putByte(x);
        putByte(y);
        putByte(z);
        putByte(w);
        return this;
    }

    // ===== Unsigned byte vector types =====

    default DataBufferWriter putVec2ub(int x, int y) {
        putUByte((byte) (x & 0xFF));
        putUByte((byte) (y & 0xFF));
        return this;
    }

    default DataBufferWriter putVec3ub(int x, int y, int z) {
        putUByte((byte) (x & 0xFF));
        putUByte((byte) (y & 0xFF));
        putUByte((byte) (z & 0xFF));
        return this;
    }

    default DataBufferWriter putVec4ub(int x, int y, int z, int w) {
        putUByte((byte) (x & 0xFF));
        putUByte((byte) (y & 0xFF));
        putUByte((byte) (z & 0xFF));
        putUByte((byte) (w & 0xFF));
        return this;
    }

    // ===== Short vector types =====

    default DataBufferWriter putVec2s(short x, short y) {
        putShort(x);
        putShort(y);
        return this;
    }

    default DataBufferWriter putVec3s(short x, short y, short z) {
        putShort(x);
        putShort(y);
        putShort(z);
        return this;
    }

    default DataBufferWriter putVec4s(short x, short y, short z, short w) {
        putShort(x);
        putShort(y);
        putShort(z);
        putShort(w);
        return this;
    }

    // ===== Unsigned short vector types =====

    default DataBufferWriter putVec2us(int x, int y) {
        putUShort((short) (x & 0xFFFF));
        putUShort((short) (y & 0xFFFF));
        return this;
    }

    default DataBufferWriter putVec3us(int x, int y, int z) {
        putUShort((short) (x & 0xFFFF));
        putUShort((short) (y & 0xFFFF));
        putUShort((short) (z & 0xFFFF));
        return this;
    }

    default DataBufferWriter putVec4us(int x, int y, int z, int w) {
        putUShort((short) (x & 0xFFFF));
        putUShort((short) (y & 0xFFFF));
        putUShort((short) (z & 0xFFFF));
        putUShort((short) (w & 0xFFFF));
        return this;
    }
    
    // ===== GLSL matrix types =====

    default DataBufferWriter putMat2(Matrix2f matrix) {
        putFloat(matrix.m00); putFloat(matrix.m10);
        putFloat(matrix.m01); putFloat(matrix.m11);
        return this;
    }

    default DataBufferWriter putMat3(Matrix3f matrix) {
        putFloat(matrix.m00); putFloat(matrix.m10); putFloat(matrix.m20);
        putFloat(matrix.m01); putFloat(matrix.m11); putFloat(matrix.m21);
        putFloat(matrix.m02); putFloat(matrix.m12); putFloat(matrix.m22);
        return this;
    }

    default DataBufferWriter putMat4(Matrix4f matrix) {
        putFloat(matrix.m00()); putFloat(matrix.m10()); putFloat(matrix.m20()); putFloat(matrix.m30());
        putFloat(matrix.m01()); putFloat(matrix.m11()); putFloat(matrix.m21()); putFloat(matrix.m31());
        putFloat(matrix.m02()); putFloat(matrix.m12()); putFloat(matrix.m22()); putFloat(matrix.m32());
        putFloat(matrix.m03()); putFloat(matrix.m13()); putFloat(matrix.m23()); putFloat(matrix.m33());
        return this;
    }

    // ===== Management methods =====

    /**
     * Advance the write position by the specified number of bytes.
     */
    void advance(int bytes);
    
    /**
     * Get the current write offset in bytes.
     */
    long getWriteOffset();
    
    /**
     * Set the current write offset.
     */
    void setWriteOffset(long offset);

    /**
     * Ensure capacity for the specified number of additional bytes.
     */
    void ensureCapacity(int additionalBytes);
    
    /**
     * Check if random access is supported.
     */
    boolean supportsRandomAccess();
    
    /**
     * Write float at specific byte offset (random access).
     */
    default void putFloatAt(long byteOffset, float value) {
        throw new UnsupportedOperationException("Random access not supported");
    }

    default void putIntAt(long byteOffset, int value) {
        throw new UnsupportedOperationException("Random access not supported");
    }

    default void putByteAt(long byteOffset, byte value) {
        throw new UnsupportedOperationException("Random access not supported");
    }

    default void putShortAt(long byteOffset, short value) {
        throw new UnsupportedOperationException("Random access not supported");
    }

    default void putDoubleAt(long byteOffset, double value) {
        throw new UnsupportedOperationException("Random access not supported");
    }
    
    /**
     * Bulk put.
     */
    default DataBufferWriter put(ByteBuffer src) {
        while (src.hasRemaining()) {
            putByte(src.get());
        }
        return this;
    }
}

