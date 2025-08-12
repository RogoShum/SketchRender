package rogo.sketch.render.data.filler;

import org.joml.Matrix2f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import rogo.sketch.render.data.format.DataFormat;

/**
 * Abstract base class for data filling operations with fluent interface
 */
public abstract class DataFiller {
    protected final DataFormat format;
    protected long currentVertex;
    protected int currentElementIndex;

    public DataFiller(DataFormat format) {
        this.format = format;
        this.currentVertex = 0;
        this.currentElementIndex = 0;
    }

    /**
     * Move to a specific vertex index
     */
    public DataFiller vertex(long index) {
        this.currentVertex = index;
        this.currentElementIndex = 0;
        return this;
    }

    /**
     * Advance to the next vertex
     */
    public DataFiller nextVertex() {
        this.currentVertex++;
        this.currentElementIndex = 0;
        return this;
    }

    /**
     * Move to a specific element within the current vertex
     */
    public DataFiller element(int elementIndex) {
        this.currentElementIndex = elementIndex;
        return this;
    }

    // Float operations
    public DataFiller floatValue(float value) {
        writeFloat(value);
        advanceElement();
        return this;
    }

    public DataFiller vec2f(float x, float y) {
        writeFloat(x);
        writeFloat(y);
        advanceElement();
        return this;
    }

    public DataFiller vec2f(Vector2f vec) {
        return vec2f(vec.x, vec.y);
    }

    public DataFiller vec3f(float x, float y, float z) {
        writeFloat(x);
        writeFloat(y);
        writeFloat(z);
        advanceElement();
        return this;
    }

    public DataFiller vec3f(Vector3f vec) {
        return vec3f(vec.x, vec.y, vec.z);
    }

    public DataFiller vec4f(float x, float y, float z, float w) {
        writeFloat(x);
        writeFloat(y);
        writeFloat(z);
        writeFloat(w);
        advanceElement();
        return this;
    }

    public DataFiller vec4f(Vector4f vec) {
        return vec4f(vec.x, vec.y, vec.z, vec.w);
    }

    // Integer operations
    public DataFiller intValue(int value) {
        writeInt(value);
        advanceElement();
        return this;
    }

    public DataFiller vec2i(int x, int y) {
        writeInt(x);
        writeInt(y);
        advanceElement();
        return this;
    }

    public DataFiller vec2i(Vector2i vec) {
        return vec2i(vec.x, vec.y);
    }

    public DataFiller vec3i(int x, int y, int z) {
        writeInt(x);
        writeInt(y);
        writeInt(z);
        advanceElement();
        return this;
    }

    public DataFiller vec3i(Vector3i vec) {
        return vec3i(vec.x, vec.y, vec.z);
    }

    public DataFiller vec4i(int x, int y, int z, int w) {
        writeInt(x);
        writeInt(y);
        writeInt(z);
        writeInt(w);
        advanceElement();
        return this;
    }

    public DataFiller vec4i(Vector4i vec) {
        return vec4i(vec.x, vec.y, vec.z, vec.w);
    }

    // Unsigned integer operations
    public DataFiller uintValue(int value) {
        writeUInt(value);
        advanceElement();
        return this;
    }

    public DataFiller vec2ui(int x, int y) {
        writeUInt(x);
        writeUInt(y);
        advanceElement();
        return this;
    }

    public DataFiller vec3ui(int x, int y, int z) {
        writeUInt(x);
        writeUInt(y);
        writeUInt(z);
        advanceElement();
        return this;
    }

    public DataFiller vec4ui(int x, int y, int z, int w) {
        writeUInt(x);
        writeUInt(y);
        writeUInt(z);
        writeUInt(w);
        advanceElement();
        return this;
    }

    // Byte operations
    public DataFiller byteValue(byte value) {
        writeByte(value);
        advanceElement();
        return this;
    }

    public DataFiller vec2b(byte x, byte y) {
        writeByte(x);
        writeByte(y);
        advanceElement();
        return this;
    }

    public DataFiller vec3b(byte x, byte y, byte z) {
        writeByte(x);
        writeByte(y);
        writeByte(z);
        advanceElement();
        return this;
    }

    public DataFiller vec4b(byte x, byte y, byte z, byte w) {
        writeByte(x);
        writeByte(y);
        writeByte(z);
        writeByte(w);
        advanceElement();
        return this;
    }

    // Unsigned byte operations
    public DataFiller ubyteValue(int value) {
        writeUByte((byte) (value & 0xFF));
        advanceElement();
        return this;
    }

    public DataFiller vec2ub(int x, int y) {
        writeUByte((byte) (x & 0xFF));
        writeUByte((byte) (y & 0xFF));
        advanceElement();
        return this;
    }

    public DataFiller vec3ub(int x, int y, int z) {
        writeUByte((byte) (x & 0xFF));
        writeUByte((byte) (y & 0xFF));
        writeUByte((byte) (z & 0xFF));
        advanceElement();
        return this;
    }

    public DataFiller vec4ub(int x, int y, int z, int w) {
        writeUByte((byte) (x & 0xFF));
        writeUByte((byte) (y & 0xFF));
        writeUByte((byte) (z & 0xFF));
        writeUByte((byte) (w & 0xFF));
        advanceElement();
        return this;
    }

    // Short operations
    public DataFiller shortValue(short value) {
        writeShort(value);
        advanceElement();
        return this;
    }

    public DataFiller vec2s(short x, short y) {
        writeShort(x);
        writeShort(y);
        advanceElement();
        return this;
    }

    public DataFiller vec3s(short x, short y, short z) {
        writeShort(x);
        writeShort(y);
        writeShort(z);
        advanceElement();
        return this;
    }

    public DataFiller vec4s(short x, short y, short z, short w) {
        writeShort(x);
        writeShort(y);
        writeShort(z);
        writeShort(w);
        advanceElement();
        return this;
    }

    // Unsigned short operations
    public DataFiller ushortValue(int value) {
        writeUShort((short) (value & 0xFFFF));
        advanceElement();
        return this;
    }

    public DataFiller vec2us(int x, int y) {
        writeUShort((short) (x & 0xFFFF));
        writeUShort((short) (y & 0xFFFF));
        advanceElement();
        return this;
    }

    public DataFiller vec3us(int x, int y, int z) {
        writeUShort((short) (x & 0xFFFF));
        writeUShort((short) (y & 0xFFFF));
        writeUShort((short) (z & 0xFFFF));
        advanceElement();
        return this;
    }

    public DataFiller vec4us(int x, int y, int z, int w) {
        writeUShort((short) (x & 0xFFFF));
        writeUShort((short) (y & 0xFFFF));
        writeUShort((short) (z & 0xFFFF));
        writeUShort((short) (w & 0xFFFF));
        advanceElement();
        return this;
    }

    // Double operations
    public DataFiller doubleValue(double value) {
        writeDouble(value);
        advanceElement();
        return this;
    }

    public DataFiller vec2d(double x, double y) {
        writeDouble(x);
        writeDouble(y);
        advanceElement();
        return this;
    }

    public DataFiller vec3d(double x, double y, double z) {
        writeDouble(x);
        writeDouble(y);
        writeDouble(z);
        advanceElement();
        return this;
    }

    public DataFiller vec4d(double x, double y, double z, double w) {
        writeDouble(x);
        writeDouble(y);
        writeDouble(z);
        writeDouble(w);
        advanceElement();
        return this;
    }

    // Matrix operations
    public DataFiller mat2(Matrix2f matrix) {
        writeFloat(matrix.m00);
        writeFloat(matrix.m01);
        writeFloat(matrix.m10);
        writeFloat(matrix.m11);
        advanceElement();
        return this;
    }

    public DataFiller mat3(Matrix3f matrix) {
        writeFloat(matrix.m00);
        writeFloat(matrix.m01);
        writeFloat(matrix.m02);
        writeFloat(matrix.m10);
        writeFloat(matrix.m11);
        writeFloat(matrix.m12);
        writeFloat(matrix.m20);
        writeFloat(matrix.m21);
        writeFloat(matrix.m22);
        advanceElement();
        return this;
    }

    public DataFiller mat4(Matrix4f matrix) {
        writeFloat(matrix.m00());
        writeFloat(matrix.m01());
        writeFloat(matrix.m02());
        writeFloat(matrix.m03());
        writeFloat(matrix.m10());
        writeFloat(matrix.m11());
        writeFloat(matrix.m12());
        writeFloat(matrix.m13());
        writeFloat(matrix.m20());
        writeFloat(matrix.m21());
        writeFloat(matrix.m22());
        writeFloat(matrix.m23());
        writeFloat(matrix.m30());
        writeFloat(matrix.m31());
        writeFloat(matrix.m32());
        writeFloat(matrix.m33());
        advanceElement();
        return this;
    }

    // Convenience methods for common vertex attributes
    public DataFiller position(float x, float y, float z) {
        return vec3f(x, y, z);
    }

    public DataFiller position(Vector3f pos) {
        return vec3f(pos);
    }

    public DataFiller normal(float x, float y, float z) {
        return vec3f(x, y, z);
    }

    public DataFiller normal(Vector3f normal) {
        return vec3f(normal);
    }

    public DataFiller uv(float u, float v) {
        return vec2f(u, v);
    }

    public DataFiller uv(Vector2f uv) {
        return vec2f(uv);
    }

    public DataFiller color(float r, float g, float b, float a) {
        return vec4f(r, g, b, a);
    }

    public DataFiller color(Vector4f color) {
        return vec4f(color);
    }

    public DataFiller colorByte(int r, int g, int b, int a) {
        return vec4ub(r, g, b, a);
    }

    // Abstract methods to be implemented by subclasses
    public abstract void writeFloat(float value);
    public abstract void writeInt(int value);
    public abstract void writeUInt(int value);
    public abstract void writeByte(byte value);
    public abstract void writeUByte(byte value);
    public abstract void writeShort(short value);
    public abstract void writeUShort(short value);
    public abstract void writeDouble(double value);

    protected void advanceElement() {
        currentElementIndex++;
        if (currentElementIndex >= format.getElementCount()) {
            nextVertex();
        }
    }

    public DataFormat getFormat() {
        return format;
    }

    public long getCurrentVertex() {
        return currentVertex;
    }

    public int getCurrentElementIndex() {
        return currentElementIndex;
    }
}
