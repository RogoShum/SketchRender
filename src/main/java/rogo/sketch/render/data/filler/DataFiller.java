package rogo.sketch.render.data.filler;

import org.joml.*;
import rogo.sketch.render.data.format.DataFormat;

/**
 * Abstract base class for data filling operations with fluent interface
 * Focuses on GLSL data types and basic operations only
 */
public abstract class DataFiller {
    protected final DataFormat format;
    protected boolean strictValidation = true;

    public DataFiller(DataFormat format) {
        this.format = format;
    }

    /**
     * Enable or disable strict data format validation
     */
    public DataFiller setStrictValidation(boolean enabled) {
        this.strictValidation = enabled;
        return this;
    }

    // ===== Core scalar data types =====
    
    /**
     * Write a single float value (GLSL: float)
     */
    public abstract DataFiller putFloat(float value);

    /**
     * Write a single int value (GLSL: int)
     */
    public abstract DataFiller putInt(int value);

    /**
     * Write a single uint value (GLSL: uint)
     */
    public abstract DataFiller putUInt(int value);

    /**
     * Write a single byte value
     */
    public abstract DataFiller putByte(byte value);

    /**
     * Write a single unsigned byte value
     */
    public abstract DataFiller putUByte(byte value);

    /**
     * Write a single short value
     */
    public abstract DataFiller putShort(short value);

    /**
     * Write a single unsigned short value
     */
    public abstract DataFiller putUShort(short value);

    /**
     * Write a single double value (GLSL: double)
     */
    public abstract DataFiller putDouble(double value);

    /**
     * Write a single bool value (GLSL: bool) - stored as byte
     */
    public final DataFiller putBool(boolean value) {
        putByte((byte) (value ? 1 : 0));
        return this;
    }

    // ===== GLSL float vector types =====

    /**
     * Write vec2 (GLSL: vec2)
     */
    public final DataFiller putVec2(float x, float y) {
        putFloat(x);
        putFloat(y);
        return this;
    }

    public final DataFiller putVec2(Vector2f vec) {
        return putVec2(vec.x, vec.y);
    }

    /**
     * Write vec3 (GLSL: vec3)
     */
    public final DataFiller putVec3(float x, float y, float z) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        return this;
    }

    public final DataFiller putVec3(Vector3f vec) {
        return putVec3(vec.x, vec.y, vec.z);
    }

    /**
     * Write vec4 (GLSL: vec4)
     */
    public final DataFiller putVec4(float x, float y, float z, float w) {
        putFloat(x);
        putFloat(y);
        putFloat(z);
        putFloat(w);
        return this;
    }

    public final DataFiller putVec4(Vector4f vec) {
        return putVec4(vec.x, vec.y, vec.z, vec.w);
    }

    // ===== GLSL integer vector types =====

    /**
     * Write ivec2 (GLSL: ivec2)
     */
    public final DataFiller putIVec2(int x, int y) {
        putInt(x);
        putInt(y);
        return this;
    }

    public final DataFiller putIVec2(Vector2i vec) {
        return putIVec2(vec.x, vec.y);
    }

    /**
     * Write ivec3 (GLSL: ivec3)
     */
    public final DataFiller putIVec3(int x, int y, int z) {
        putInt(x);
        putInt(y);
        putInt(z);
        return this;
    }

    public final DataFiller putIVec3(Vector3i vec) {
        return putIVec3(vec.x, vec.y, vec.z);
    }

    /**
     * Write ivec4 (GLSL: ivec4)
     */
    public final DataFiller putIVec4(int x, int y, int z, int w) {
        putInt(x);
        putInt(y);
        putInt(z);
        putInt(w);
        return this;
    }

    public final DataFiller putIVec4(Vector4i vec) {
        return putIVec4(vec.x, vec.y, vec.z, vec.w);
    }

    // ===== GLSL unsigned integer vector types =====

    /**
     * Write uvec2 (GLSL: uvec2)
     */
    public final DataFiller putUVec2(int x, int y) {
        putUInt(x);
        putUInt(y);
        return this;
    }

    /**
     * Write uvec3 (GLSL: uvec3)
     */
    public final DataFiller putUVec3(int x, int y, int z) {
        putUInt(x);
        putUInt(y);
        putUInt(z);
        return this;
    }

    /**
     * Write uvec4 (GLSL: uvec4)
     */
    public final DataFiller putUVec4(int x, int y, int z, int w) {
        putUInt(x);
        putUInt(y);
        putUInt(z);
        putUInt(w);
        return this;
    }

    // ===== GLSL boolean vector types =====

    /**
     * Write bvec2 (GLSL: bvec2)
     */
    public final DataFiller putBVec2(boolean x, boolean y) {
        putBool(x);
        putBool(y);
        return this;
    }

    /**
     * Write bvec3 (GLSL: bvec3)
     */
    public final DataFiller putBVec3(boolean x, boolean y, boolean z) {
        putBool(x);
        putBool(y);
        putBool(z);
        return this;
    }

    /**
     * Write bvec4 (GLSL: bvec4)
     */
    public final DataFiller putBVec4(boolean x, boolean y, boolean z, boolean w) {
        putBool(x);
        putBool(y);
        putBool(z);
        putBool(w);
        return this;
    }

    // ===== Byte vector types =====

    /**
     * Write 2-component byte vector
     */
    public final DataFiller putVec2b(byte x, byte y) {
        putByte(x);
        putByte(y);
        return this;
    }

    /**
     * Write 3-component byte vector
     */
    public final DataFiller putVec3b(byte x, byte y, byte z) {
        putByte(x);
        putByte(y);
        putByte(z);
        return this;
    }

    /**
     * Write 4-component byte vector
     */
    public final DataFiller putVec4b(byte x, byte y, byte z, byte w) {
        putByte(x);
        putByte(y);
        putByte(z);
        putByte(w);
        return this;
    }

    // ===== Unsigned byte vector types =====

    /**
     * Write 2-component unsigned byte vector
     */
    public final DataFiller putVec2ub(int x, int y) {
        putUByte((byte) (x & 0xFF));
        putUByte((byte) (y & 0xFF));
        return this;
    }

    /**
     * Write 3-component unsigned byte vector
     */
    public final DataFiller putVec3ub(int x, int y, int z) {
        putUByte((byte) (x & 0xFF));
        putUByte((byte) (y & 0xFF));
        putUByte((byte) (z & 0xFF));
        return this;
    }

    /**
     * Write 4-component unsigned byte vector
     */
    public final DataFiller putVec4ub(int x, int y, int z, int w) {
        putUByte((byte) (x & 0xFF));
        putUByte((byte) (y & 0xFF));
        putUByte((byte) (z & 0xFF));
        putUByte((byte) (w & 0xFF));
        return this;
    }

    // ===== Short vector types =====

    /**
     * Write 2-component short vector
     */
    public final DataFiller putVec2s(short x, short y) {
        putShort(x);
        putShort(y);
        return this;
    }

    /**
     * Write 3-component short vector
     */
    public final DataFiller putVec3s(short x, short y, short z) {
        putShort(x);
        putShort(y);
        putShort(z);
        return this;
    }

    /**
     * Write 4-component short vector
     */
    public final DataFiller putVec4s(short x, short y, short z, short w) {
        putShort(x);
        putShort(y);
        putShort(z);
        putShort(w);
        return this;
    }

    // ===== Unsigned short vector types =====

    /**
     * Write 2-component unsigned short vector
     */
    public final DataFiller putVec2us(int x, int y) {
        putUShort((short) (x & 0xFFFF));
        putUShort((short) (y & 0xFFFF));
        return this;
    }

    /**
     * Write 3-component unsigned short vector
     */
    public final DataFiller putVec3us(int x, int y, int z) {
        putUShort((short) (x & 0xFFFF));
        putUShort((short) (y & 0xFFFF));
        putUShort((short) (z & 0xFFFF));
        return this;
    }

    /**
     * Write 4-component unsigned short vector
     */
    public final DataFiller putVec4us(int x, int y, int z, int w) {
        putUShort((short) (x & 0xFFFF));
        putUShort((short) (y & 0xFFFF));
        putUShort((short) (z & 0xFFFF));
        putUShort((short) (w & 0xFFFF));
        return this;
    }

    // ===== Double vector types =====

    /**
     * Write dvec2 (GLSL: dvec2)
     */
    public final DataFiller putDVec2(double x, double y) {
        putDouble(x);
        putDouble(y);
        return this;
    }

    /**
     * Write dvec3 (GLSL: dvec3)
     */
    public final DataFiller putDVec3(double x, double y, double z) {
        putDouble(x);
        putDouble(y);
        putDouble(z);
        return this;
    }

    /**
     * Write dvec4 (GLSL: dvec4)
     */
    public final DataFiller putDVec4(double x, double y, double z, double w) {
        putDouble(x);
        putDouble(y);
        putDouble(z);
        putDouble(w);
        return this;
    }

    // ===== GLSL matrix types =====

    /**
     * Write mat2 (GLSL: mat2) - 2x2 float matrix
     */
    public final DataFiller putMat2(Matrix2f matrix) {
        // Column-major order as per GLSL standard
        putFloat(matrix.m00); putFloat(matrix.m10); // column 0
        putFloat(matrix.m01); putFloat(matrix.m11); // column 1
        return this;
    }

    /**
     * Write mat3 (GLSL: mat3) - 3x3 float matrix
     */
    public final DataFiller putMat3(Matrix3f matrix) {
        // Column-major order
        putFloat(matrix.m00); putFloat(matrix.m10); putFloat(matrix.m20); // column 0
        putFloat(matrix.m01); putFloat(matrix.m11); putFloat(matrix.m21); // column 1
        putFloat(matrix.m02); putFloat(matrix.m12); putFloat(matrix.m22); // column 2
        return this;
    }

    /**
     * Write mat4 (GLSL: mat4) - 4x4 float matrix
     */
    public final DataFiller putMat4(Matrix4f matrix) {
        // Column-major order
        putFloat(matrix.m00()); putFloat(matrix.m10()); putFloat(matrix.m20()); putFloat(matrix.m30()); // column 0
        putFloat(matrix.m01()); putFloat(matrix.m11()); putFloat(matrix.m21()); putFloat(matrix.m31()); // column 1
        putFloat(matrix.m02()); putFloat(matrix.m12()); putFloat(matrix.m22()); putFloat(matrix.m32()); // column 2
        putFloat(matrix.m03()); putFloat(matrix.m13()); putFloat(matrix.m23()); putFloat(matrix.m33()); // column 3
        return this;
    }

    // ===== Random access methods (optional - may throw UnsupportedOperationException) =====

    /**
     * Write float at specific byte offset (random access)
     */
    public void putFloatAt(long byteOffset, float value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    /**
     * Write int at specific byte offset (random access)
     */
    public void putIntAt(long byteOffset, int value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    /**
     * Write uint at specific byte offset (random access)
     */
    public void putUIntAt(long byteOffset, int value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    /**
     * Write byte at specific byte offset (random access)
     */
    public void putByteAt(long byteOffset, byte value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    /**
     * Write unsigned byte at specific byte offset (random access)
     */
    public void putUByteAt(long byteOffset, byte value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    /**
     * Write short at specific byte offset (random access)
     */
    public void putShortAt(long byteOffset, short value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    /**
     * Write unsigned short at specific byte offset (random access)
     */
    public void putUShortAt(long byteOffset, short value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    /**
     * Write double at specific byte offset (random access)
     */
    public void putDoubleAt(long byteOffset, double value) {
        throw new UnsupportedOperationException("Random access not supported by " + getClass().getSimpleName());
    }

    // ===== Utility methods =====

    /**
     * Get the data format
     */
    public final DataFormat getFormat() {
        return format;
    }

    /**
     * Check if random access is supported
     */
    public boolean supportsRandomAccess() {
        return false; // Override in subclasses that support random access
    }

    /**
     * Finish data filling and prepare for use
     */
    public abstract void finish();
}