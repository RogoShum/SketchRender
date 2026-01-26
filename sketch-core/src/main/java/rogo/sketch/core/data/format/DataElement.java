package rogo.sketch.core.data.format;

import rogo.sketch.core.data.DataType;

import java.util.Objects;

/**
 * Describes a single element within a data format (used for vertices, uniforms,
 * SSBO, UBO, etc.)
 */
public class DataElement {
    private final String name;
    private final DataType dataType;
    private final int index;
    private final boolean normalized;
    private int offset;

    public DataElement(String name, DataType dataType, int index) {
        this(name, dataType, index, false);
    }

    public DataElement(String name, DataType dataType, int index, boolean normalized) {
        this.name = name;
        this.dataType = dataType;
        this.index = index;
        this.normalized = normalized;
        this.offset = 0; // Will be set by DataFormat
    }

    public String getName() {
        return name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public int getIndex() {
        return index;
    }

    public boolean isNormalized() {
        return normalized;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getGLType() {
        return dataType.getGLType();
    }

    public int getComponentCount() {
        return dataType.getComponentCount();
    }

    public int getStride() {
        return dataType.getStride();
    }

    public int getComponentSize() {
        return dataType.getComponentSize();
    }

    public DataElement copy(int index) {
        return new DataElement(name, dataType, index, normalized);
    }

    /**
     * Check if this element is compatible with another element (for shader
     * matching)
     */
    public boolean isCompatibleWith(DataElement other) {
        // Name doesn't need to match for compatibility
        return this.dataType.isCompatibleWith(other.dataType) &&
                this.index == other.index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DataElement that = (DataElement) o;
        return index == that.index &&
                normalized == that.normalized &&
                offset == that.offset &&
                dataType == that.dataType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataType, index, normalized, offset);
    }

    @Override
    public String toString() {
        return "DataElement{" +
                "name='" + name + '\'' +
                ", dataType=" + dataType +
                ", index=" + index +
                ", normalized=" + normalized +
                ", offset=" + offset +
                '}';
    }
}