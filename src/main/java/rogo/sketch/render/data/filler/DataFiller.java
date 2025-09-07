package rogo.sketch.render.data.filler;

import org.joml.*;
import rogo.sketch.render.data.DataType;
import rogo.sketch.render.data.format.DataFormat;

import java.lang.Math;

/**
 * Abstract base class for data filling operations with fluent interface
 * Supports both sequential and indexed filling modes
 */
public abstract class DataFiller {
    protected final DataFormat format;
    protected final int vertexStride;
    protected long currentVertex;
    protected int currentElementIndex;

    // Validation state
    protected boolean strictValidation = true;
    protected boolean autoAdvanceElements = true;

    // Filling mode
    protected boolean indexedMode = false;  // false = sequential, true = indexed

    public DataFiller(DataFormat format) {
        this.format = format;
        this.vertexStride = format.getStride();
        this.currentVertex = 0;
        this.currentElementIndex = 0;
    }

    /**
     * Enable or disable strict data format validation
     */
    public DataFiller setStrictValidation(boolean enabled) {
        this.strictValidation = enabled;
        return this;
    }

    /**
     * Enable or disable automatic element advancement
     */
    public DataFiller setAutoAdvanceElements(boolean enabled) {
        this.autoAdvanceElements = enabled;
        return this;
    }

    /**
     * Switch to indexed filling mode for async operations
     */
    public DataFiller setIndexedMode(boolean enabled) {
        this.indexedMode = enabled;
        return this;
    }

    /**
     * Check if in indexed mode
     */
    public boolean isIndexedMode() {
        return indexedMode;
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
        validateCurrentElement(DataType.FLOAT);
        writeFloat(value);
        advanceElement();
        return this;
    }

    /**
     * Float value with explicit vertex index for async operations
     */
    public DataFiller floatValue(int vertexIndex, int elementIndex, float value) {
        if (indexedMode) {
            writeFloatAt(vertexIndex, elementIndex, value);
        } else {
            // Fall back to sequential mode
            floatValue(value);
        }
        return this;
    }

    public DataFiller vec2f(float x, float y) {
        validateCurrentElement(DataType.VEC2);
        writeFloat(x);
        writeFloat(y);
        advanceElement();
        return this;
    }

    public DataFiller vec2f(Vector2f vec) {
        return vec2f(vec.x, vec.y);
    }

    public DataFiller vec3f(float x, float y, float z) {
        validateCurrentElement(DataType.VEC3);
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
        validateCurrentElement(DataType.VEC4);
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
        validateCurrentElement(DataType.INT);
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
        validateCurrentElement(DataType.MAT4);
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

    // Indexed writing methods for async operations
    public abstract void writeFloatAt(int vertexIndex, int elementIndex, float value);

    public abstract void writeIntAt(int vertexIndex, int elementIndex, int value);

    public abstract void writeUIntAt(int vertexIndex, int elementIndex, int value);

    public abstract void writeByteAt(int vertexIndex, int elementIndex, byte value);

    public abstract void writeUByteAt(int vertexIndex, int elementIndex, byte value);

    public abstract void writeShortAt(int vertexIndex, int elementIndex, short value);

    public abstract void writeUShortAt(int vertexIndex, int elementIndex, short value);

    public abstract void writeDoubleAt(int vertexIndex, int elementIndex, double value);

    // High-level indexed writing methods
    /**
     * Write vector2 at specific vertex and element index
     */
    public DataFiller vec2fAt(int vertexIndex, int elementIndex, float x, float y) {
        if (indexedMode) {
            writeFloatAt(vertexIndex, elementIndex, x);
            writeFloatAt(vertexIndex, elementIndex + 1, y);
        } else {
            vec2f(x, y);
        }
        return this;
    }

    /**
     * Write vector3 at specific vertex and element index
     */
    public DataFiller vec3fAt(int vertexIndex, int elementIndex, float x, float y, float z) {
        if (indexedMode) {
            writeFloatAt(vertexIndex, elementIndex, x);
            writeFloatAt(vertexIndex, elementIndex + 1, y);
            writeFloatAt(vertexIndex, elementIndex + 2, z);
        } else {
            vec3f(x, y, z);
        }
        return this;
    }

    /**
     * Write vector4 at specific vertex and element index
     */
    public DataFiller vec4fAt(int vertexIndex, int elementIndex, float x, float y, float z, float w) {
        if (indexedMode) {
            writeFloatAt(vertexIndex, elementIndex, x);
            writeFloatAt(vertexIndex, elementIndex + 1, y);
            writeFloatAt(vertexIndex, elementIndex + 2, z);
            writeFloatAt(vertexIndex, elementIndex + 3, w);
        } else {
            vec4f(x, y, z, w);
        }
        return this;
    }

    /**
     * Write position at specific vertex index (assumes first 3 elements are position)
     */
    public DataFiller positionAt(int vertexIndex, float x, float y, float z) {
        return vec3fAt(vertexIndex, 0, x, y, z);
    }

    /**
     * Write color at specific vertex and element index
     */
    public DataFiller colorAt(int vertexIndex, int elementIndex, float r, float g, float b, float a) {
        return vec4fAt(vertexIndex, elementIndex, r, g, b, a);
    }

    /**
     * Calculate byte position for a specific vertex and element
     */
    protected int calculateBytePosition(int vertexIndex, int elementIndex) {
        if (elementIndex >= format.getElementCount()) {
            throw new IndexOutOfBoundsException("Element index " + elementIndex + " out of bounds for format with " + format.getElementCount() + " elements");
        }

        int vertexByteOffset = vertexIndex * vertexStride;
        int elementByteOffset = format.getElements().get(elementIndex).getOffset();
        return vertexByteOffset + elementByteOffset;
    }

    /**
     * Complete the data filling process
     * Subclasses should implement their specific finalization logic
     */
    public abstract void end();

    protected void advanceElement() {
        if (autoAdvanceElements) {
            currentElementIndex++;
            if (currentElementIndex >= format.getElementCount()) {
                nextVertex();
            }
        }
    }

    /**
     * Validate that the current element matches the expected data type
     */
    protected void validateCurrentElement(DataType expectedType) {
        if (!strictValidation) return;

        if (currentElementIndex >= format.getElementCount()) {
            throw new IllegalStateException("Attempting to write beyond format element count. " +
                    "Current element index: " + currentElementIndex +
                    ", Format element count: " + format.getElementCount());
        }

        var currentElement = format.getElements().get(currentElementIndex);
        if (currentElement.getDataType() != expectedType) {
            throw new IllegalArgumentException("Data type mismatch for element '" +
                    currentElement.getName() + "' at index " + currentElementIndex +
                    ". Expected: " + expectedType + ", Format expects: " + currentElement.getDataType());
        }
    }

    /**
     * Validate that we have enough components for the data type
     */
    protected void validateDataTypeComponents(DataType dataType, int providedComponents) {
        if (!strictValidation) return;

        int expectedComponents = dataType.getComponentCount();
        if (providedComponents != expectedComponents) {
            throw new IllegalArgumentException("Component count mismatch for data type " + dataType +
                    ". Expected: " + expectedComponents + ", Provided: " + providedComponents);
        }
    }

    /**
     * Check if the current vertex is complete
     * Note: When advanceElement() auto-calls nextVertex(), currentElementIndex resets to 0
     * but the previous vertex was actually complete. We need to handle this case.
     */
    public boolean isCurrentVertexComplete() {
        // If currentElementIndex is 0 and we have filled at least one vertex,
        // it means the previous vertex was completed by advanceElement()
        if (currentElementIndex == 0 && currentVertex > 0) {
            return true;
        }
        return currentElementIndex >= format.getElementCount();
    }

    /**
     * Get the remaining elements needed to complete current vertex
     */
    public int getRemainingElementsInVertex() {
        return Math.max(0, format.getElementCount() - currentElementIndex);
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
