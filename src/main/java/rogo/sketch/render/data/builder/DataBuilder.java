package rogo.sketch.render.data.builder;

import org.joml.*;
import rogo.sketch.render.data.format.DataFormat;

import java.nio.ByteBuffer;

/**
 * Interface for building data buffers (vertices, uniforms, SSBOs).
 * Provides a unified API for writing data to various backends (ByteBuffer, Unsafe Pointer, etc.).
 *
 * This interface replaces the legacy DataFiller and BufferBuilder concepts.
 */
public interface DataBuilder {

    /**
     * Get the data format describing the layout of this buffer.
     */
    DataFormat getFormat();

    /**
     * Ensure the underlying storage has enough capacity for the given number of bytes.
     */
    void ensureCapacity(int bytes);

    // ===== Scalar Types =====

    DataBuilder putByte(byte value);
    DataBuilder putShort(short value);
    DataBuilder putInt(int value);
    DataBuilder putLong(long value);
    DataBuilder putFloat(float value);
    DataBuilder putDouble(double value);
    DataBuilder putBoolean(boolean value);

    // ===== Unsigned Types (treated as higher-precision signed types or raw bits) =====

    default DataBuilder putUByte(int value) {
        return putByte((byte) (value & 0xFF));
    }

    default DataBuilder putUShort(int value) {
        return putShort((short) (value & 0xFFFF));
    }

    default DataBuilder putUInt(long value) {
        return putInt((int) (value & 0xFFFFFFFFL));
    }

    // ===== Vector Types (GLSL Semantic) =====

    default DataBuilder putVec2(float x, float y) {
        putFloat(x);
        putFloat(y);
        return this;
    }

    default DataBuilder putVec3(float x, float y, float z) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        return this;
    }

    default DataBuilder putVec4(float x, float y, float z, float w) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        putFloat(w);
        return this;
    }

    default DataBuilder putIVec2(int x, int y) {
        putInt(x);
        putInt(y);
        return this;
    }

    default DataBuilder putIVec3(int x, int y, int z) {
        putInt(x);
        putInt(y);
        putInt(z);
        return this;
    }

    default DataBuilder putIVec4(int x, int y, int z, int w) {
        putInt(x);
        putInt(y);
        putInt(z);
        putInt(w);
        return this;
    }

    // ===== JOML Integration =====

    default DataBuilder putVec2(Vector2fc v) {
        return putVec2(v.x(), v.y());
    }

    default DataBuilder putVec3(Vector3fc v) {
        return putVec3(v.x(), v.y(), v.z());
    }

    default DataBuilder putVec4(Vector4fc v) {
        return putVec4(v.x(), v.y(), v.z(), v.w());
    }

    default DataBuilder putMat3(Matrix3fc m) {
        // Column-major
        putFloat(m.m00()); putFloat(m.m10()); putFloat(m.m20());
        putFloat(m.m01()); putFloat(m.m11()); putFloat(m.m21());
        putFloat(m.m02()); putFloat(m.m12()); putFloat(m.m22());
        return this;
    }

    default DataBuilder putMat4(Matrix4fc m) {
        // Column-major
        putFloat(m.m00()); putFloat(m.m10()); putFloat(m.m20()); putFloat(m.m30());
        putFloat(m.m01()); putFloat(m.m11()); putFloat(m.m21()); putFloat(m.m31());
        putFloat(m.m02()); putFloat(m.m12()); putFloat(m.m22()); putFloat(m.m32());
        putFloat(m.m03()); putFloat(m.m13()); putFloat(m.m23()); putFloat(m.m33());
        return this;
    }

    // ===== Raw Data =====

    DataBuilder putBytes(byte[] bytes);
    DataBuilder putBytes(ByteBuffer buffer);

    // ===== Cursor Management =====

    /**
     * Advance the write cursor by a number of bytes without writing.
     * Effectively writes zeros or garbage (depending on implementation).
     */
    void skip(int bytes);

    /**
     * Get the current byte offset from the start of the buffer.
     */
    long offset();

    /**
     * Reset the builder to the beginning (clear).
     */
    void reset();

    /**
     * Finish building and prepare the data.
     * For some implementations, this might trigger a flush or sort.
     */
    void finish();
}

