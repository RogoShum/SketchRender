package rogo.sketch.render.data.format;

import rogo.sketch.render.shader.uniform.DataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Describes the layout of data in a buffer (used for vertices, uniforms, SSBO, UBO, etc.)
 */
public class DataFormat {
    private final List<DataElement> elements;
    private final int stride;
    private final String name;

    public DataFormat(String name, DataElement... elements) {
        this.name = name;
        this.elements = new ArrayList<>(Arrays.asList(elements));
        this.stride = calculateStride();
    }

    public DataFormat(String name, List<DataElement> elements) {
        this.name = name;
        this.elements = new ArrayList<>(elements);
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

    public List<DataElement> getElements() {
        return new ArrayList<>(elements);
    }

    public int getStride() {
        return stride;
    }

    public String getName() {
        return name;
    }

    public int getElementCount() {
        return elements.size();
    }

    public DataElement getElement(int index) {
        return elements.get(index);
    }

    public DataElement getElement(String name) {
        return elements.stream()
                .filter(element -> element.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if this format is compatible with another format (for shader matching)
     */
    public boolean isCompatibleWith(DataFormat other) {
        if (this.elements.size() != other.elements.size()) {
            return false;
        }

        for (int i = 0; i < elements.size(); i++) {
            DataElement thisElement = this.elements.get(i);
            DataElement otherElement = other.elements.get(i);
            
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
        if (this.elements.size() != other.elements.size()) {
            return false;
        }

        for (int i = 0; i < elements.size(); i++) {
            DataElement thisElement = this.elements.get(i);
            DataElement otherElement = other.elements.get(i);
            
            if (!thisElement.equals(otherElement)) {
                return false;
            }
        }
        
        return true;
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

        private Builder(String name) {
            this.name = name;
        }

        public Builder add(String elementName, DataType dataType) {
            elements.add(new DataElement(elementName, dataType, elements.size()));
            return this;
        }

        public Builder add(String elementName, DataType dataType, boolean normalized) {
            elements.add(new DataElement(elementName, dataType, elements.size(), normalized));
            return this;
        }

        public Builder floatAttribute(String name) {
            return add(name, DataType.FLOAT);
        }

        public Builder vec2Attribute(String name) {
            return add(name, DataType.VEC2);
        }

        public Builder vec3Attribute(String name) {
            return add(name, DataType.VEC3);
        }

        public Builder vec4Attribute(String name) {
            return add(name, DataType.VEC4);
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
            return new DataFormat(name, elements);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFormat that = (DataFormat) o;
        return stride == that.stride && 
               Objects.equals(elements, that.elements) && 
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, stride, name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DataFormat{name='").append(name).append("', stride=").append(stride).append(", elements=[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(elements.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }
}
