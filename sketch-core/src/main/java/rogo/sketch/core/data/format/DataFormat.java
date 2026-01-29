package rogo.sketch.core.data.format;

import rogo.sketch.core.data.DataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Describes the layout of data in a buffer (used for vertices, uniforms, SSBO,
 * UBO, etc.)
 */
public class DataFormat {
    private final DataElement[] elements;
    private final int stride;
    private final String name;

    public DataFormat(String name, DataElement... elements) {
        this.name = name;
        this.elements = elements;
        this.stride = calculateStride();
    }

    public DataFormat(String name, List<DataElement> elements) {
        this.name = name;
        this.elements = elements.toArray(new DataElement[0]);
        this.stride = calculateStride();
    }

    private int calculateStride() {
        int totalStride = 0;
        int currentOffset = 0;

        for (DataElement element : elements) {
            // Update element offset
            element.setOffset(currentOffset);

            // Add padding for alignment if needed
            int alignment = element.getDataType().getComponentSize();
            int misalignment = currentOffset % alignment;
            if (misalignment != 0) {
                currentOffset += alignment - misalignment;
                element.setOffset(currentOffset);
            }

            currentOffset += element.getDataType().getStride();
            totalStride = currentOffset;
        }

        return totalStride;
    }

    public DataElement[] getElements() {
        return elements;
    }

    public int getStride() {
        return stride;
    }

    public String getName() {
        return name;
    }

    public int getElementCount() {
        return elements.length;
    }

    public DataElement getElement(int index) {
        return elements[index];
    }

    public DataElement getSortKeyElement() {
        for (DataElement e : elements) {
            if (e.isSortKey()) return e;
        }
        return null;
    }

    /**
     * Check if this format is compatible with another format (for shader matching)
     */
    public boolean isCompatibleWith(DataFormat other) {
        if (this.elements.length != other.elements.length) {
            return false;
        }

        for (int i = 0; i < elements.length; i++) {
            DataElement thisElement = this.elements[i];
            DataElement otherElement = other.elements[i];

            if (!thisElement.isCompatibleWith(otherElement)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if this format exactly matches another format
     */
    public boolean matches(DataFormat other) {
        if (this.elements.length != other.elements.length) {
            return false;
        }

        for (int i = 0; i < elements.length; i++) {
            DataElement thisElement = this.elements[i];
            DataElement otherElement = other.elements[i];

            if (!thisElement.equals(otherElement)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if this format contains all elements of the other format.
     */
    public boolean contains(DataFormat other) {
        for (DataElement otherElement : other.elements) {
            DataElement thisElement = null;
            for (DataElement e : elements) {
                if (e.getIndex() == otherElement.getIndex()) {
                    thisElement = e;
                    break;
                }
            }

            if (thisElement == null || !thisElement.isCompatibleWith(otherElement)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create a new DataFormat with all attribute indices shifted by the given
     * offset.
     */
    public DataFormat withAttributeIndexOffset(int offset) {
        if (offset == 0)
            return this;

        List<DataElement> newElements = new ArrayList<>(elements.length);
        for (DataElement element : elements) {
            newElements.add(element.copy(element.getIndex() + offset));
        }
        // Use constructor that takes list to preserve stride and name
        return new DataFormat(this.name + "_shifted_" + offset, newElements);
    }

    /**
     * Create a builder for constructing data formats
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<DataElement> elements = new ArrayList<>();
        private int nextLocation = 0;

        private Builder(String name) {
            this.name = name;
        }

        public Builder add(int location, String elementName, DataType dataType) {
            return add(location, elementName, dataType, false, false, false);
        }

        public Builder add(int location, String elementName, DataType dataType, boolean normalized, boolean sortKey, boolean padding) {
            // Check for duplicate locations
            for (DataElement element : elements) {
                if (element.getIndex() == location) {
                    throw new IllegalArgumentException("Duplicate attribute location: " + location);
                }
            }
            elements.add(new DataElement(elementName, dataType, location, normalized, sortKey, padding));
            // Update nextLocation to be at least location + 1, so auto-add works if mixed
            if (location >= nextLocation) {
                nextLocation = location + 1;
            }
            return this;
        }

        public Builder add(String elementName, DataType dataType) {
            return add(nextLocation, elementName, dataType, false, false, false);
        }

        public Builder add(String elementName, DataType dataType, boolean normalized, boolean sortKey, boolean padding) {
            return add(nextLocation, elementName, dataType, normalized, sortKey, padding);
        }

        public Builder floatAttribute(int location, String name) {
            return add(location, name, DataType.FLOAT);
        }

        public Builder vec2Attribute(int location, String name) {
            return add(location, name, DataType.VEC2F);
        }

        public Builder vec3Attribute(int location, String name) {
            return add(location, name, DataType.VEC3F);
        }

        public Builder vec4Attribute(int location, String name) {
            return add(location, name, DataType.VEC4F);
        }

        public Builder intAttribute(int location, String name) {
            return add(location, name, DataType.INT);
        }

        public Builder vec2iAttribute(int location, String name) {
            return add(location, name, DataType.VEC2I);
        }

        public Builder vec3iAttribute(int location, String name) {
            return add(location, name, DataType.VEC3I);
        }

        public Builder vec4iAttribute(int location, String name) {
            return add(location, name, DataType.VEC4I);
        }

        public Builder byteAttribute(int location, String name) {
            return add(location, name, DataType.BYTE);
        }

        public Builder ubyteAttribute(int location, String name) {
            return add(location, name, DataType.UBYTE);
        }

        public Builder shortAttribute(int location, String name) {
            return add(location, name, DataType.SHORT);
        }

        public Builder ushortAttribute(int location, String name) {
            return add(location, name, DataType.USHORT);
        }

        public Builder mat4Attribute(int location, String name) {
            return add(location, name, DataType.MAT4);
        }

        public Builder floatAttribute(String name) {
            return add(name, DataType.FLOAT);
        }

        public Builder vec2Attribute(String name) {
            return add(name, DataType.VEC2F);
        }

        public Builder vec3Attribute(String name) {
            return add(name, DataType.VEC3F);
        }

        public Builder vec4Attribute(String name) {
            return add(name, DataType.VEC4F);
        }

        public Builder intAttribute(String name) {
            return add(name, DataType.INT);
        }

        public Builder vec2iAttribute(String name) {
            return add(name, DataType.VEC2I);
        }

        public Builder vec3iAttribute(String name) {
            return add(name, DataType.VEC3I);
        }

        public Builder vec4iAttribute(String name) {
            return add(name, DataType.VEC4I);
        }

        public Builder byteAttribute(String name) {
            return add(name, DataType.BYTE);
        }

        public Builder ubyteAttribute(String name) {
            return add(name, DataType.UBYTE);
        }

        public Builder shortAttribute(String name) {
            return add(name, DataType.SHORT);
        }

        public Builder ushortAttribute(String name) {
            return add(name, DataType.USHORT);
        }

        public Builder mat4Attribute(String name) {
            return add(name, DataType.MAT4);
        }

        public DataFormat build() {
            // Sort elements by index for consistency
            elements.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
            return new DataFormat(name, elements);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DataFormat that = (DataFormat) o;
        return stride == that.stride &&
                Arrays.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(elements), stride);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DataFormat{name='").append(name).append("', stride=").append(stride).append(", elements=[");
        for (int i = 0; i < elements.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(elements[i]);
        }
        sb.append("]}");
        return sb.toString();
    }
}
