package rogo.sketch.core.data.layout;

import rogo.sketch.core.data.type.ValueType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Generic structured memory layout used by vertex, uniform, storage, and other
 * buffer-backed data definitions.
 */
public class StructLayout {
    private final FieldSpec[] fields;
    private final int stride;
    private final String name;

    public StructLayout(String name, FieldSpec... fields) {
        this(name, Arrays.asList(fields));
    }

    public StructLayout(String name, List<FieldSpec> fields) {
        this.name = name;
        this.fields = fields.toArray(new FieldSpec[0]);
        this.stride = calculateStride();
    }

    private int calculateStride() {
        int currentOffset = 0;
        for (FieldSpec field : fields) {
            int alignment = field.getComponentSize();
            int misalignment = currentOffset % alignment;
            if (misalignment != 0) {
                currentOffset += alignment - misalignment;
            }
            field.setOffset(currentOffset);
            currentOffset += field.getStride();
        }
        return currentOffset;
    }

    public FieldSpec[] getFields() {
        return fields;
    }

    public FieldSpec[] getElements() {
        return getFields();
    }

    public int getStride() {
        return stride;
    }

    public String getName() {
        return name;
    }

    public int getFieldCount() {
        return fields.length;
    }

    public int getElementCount() {
        return getFieldCount();
    }

    public FieldSpec getField(int index) {
        return fields[index];
    }

    public FieldSpec getElement(int index) {
        return getField(index);
    }

    public FieldSpec getFieldBySlot(int slot) {
        for (FieldSpec field : fields) {
            if (field.getSlot() == slot) {
                return field;
            }
        }
        return null;
    }

    public FieldSpec getElementBySlot(int slot) {
        return getFieldBySlot(slot);
    }

    public FieldSpec getSortKeyField() {
        for (FieldSpec field : fields) {
            if (field.isSortKey()) {
                return field;
            }
        }
        return null;
    }

    public FieldSpec getSortKeyElement() {
        return getSortKeyField();
    }

    public boolean isCompatibleWith(StructLayout other) {
        if (other == null || fields.length != other.fields.length) {
            return false;
        }
        for (int i = 0; i < fields.length; i++) {
            if (!fields[i].isCompatibleWith(other.fields[i])) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(StructLayout other) {
        return other != null && Arrays.equals(fields, other.fields);
    }

    public boolean contains(StructLayout other) {
        if (other == null) {
            return false;
        }
        for (FieldSpec otherField : other.fields) {
            FieldSpec thisField = getFieldBySlot(otherField.getSlot());
            if (thisField == null || !thisField.isCompatibleWith(otherField)) {
                return false;
            }
        }
        return true;
    }

    public StructLayout withSlotOffset(int slotOffset) {
        if (slotOffset == 0) {
            return this;
        }
        List<FieldSpec> shifted = new ArrayList<>(fields.length);
        for (FieldSpec field : fields) {
            shifted.add(field.copy(field.getSlot() + slotOffset));
        }
        return new StructLayout(name + "_shifted_" + slotOffset, shifted);
    }

    public StructLayout withAttributeIndexOffset(int slotOffset) {
        return withSlotOffset(slotOffset);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<FieldSpec> fields = new ArrayList<>();
        private int nextSlot = 0;

        private Builder(String name) {
            this.name = name;
        }

        public Builder add(int slot, String fieldName, ValueType valueType) {
            return add(slot, fieldName, valueType, false, false, false);
        }

        public Builder addField(int slot, String fieldName, ValueType valueType) {
            return add(slot, fieldName, valueType, false, false, false);
        }

        public Builder add(int slot, String fieldName, ValueType valueType, boolean normalized, boolean sortKey, boolean padding) {
            for (FieldSpec field : fields) {
                if (field.getSlot() == slot) {
                    throw new IllegalArgumentException("Duplicate field slot: " + slot);
                }
            }
            fields.add(new FieldSpec(fieldName, valueType, slot, normalized, sortKey, padding));
            if (slot >= nextSlot) {
                nextSlot = slot + 1;
            }
            return this;
        }

        public Builder add(String fieldName, ValueType valueType) {
            return add(nextSlot, fieldName, valueType, false, false, false);
        }

        public Builder add(String fieldName, ValueType valueType, boolean normalized, boolean sortKey, boolean padding) {
            return add(nextSlot, fieldName, valueType, normalized, sortKey, padding);
        }

        public Builder floatField(int slot, String name) { return add(slot, name, ValueType.FLOAT); }
        public Builder vec2Field(int slot, String name) { return add(slot, name, ValueType.VEC2F); }
        public Builder vec3Field(int slot, String name) { return add(slot, name, ValueType.VEC3F); }
        public Builder vec4Field(int slot, String name) { return add(slot, name, ValueType.VEC4F); }
        public Builder intField(int slot, String name) { return add(slot, name, ValueType.INT); }
        public Builder vec2iField(int slot, String name) { return add(slot, name, ValueType.VEC2I); }
        public Builder vec3iField(int slot, String name) { return add(slot, name, ValueType.VEC3I); }
        public Builder vec4iField(int slot, String name) { return add(slot, name, ValueType.VEC4I); }
        public Builder byteField(int slot, String name) { return add(slot, name, ValueType.BYTE); }
        public Builder ubyteField(int slot, String name) { return add(slot, name, ValueType.UBYTE); }
        public Builder shortField(int slot, String name) { return add(slot, name, ValueType.SHORT); }
        public Builder ushortField(int slot, String name) { return add(slot, name, ValueType.USHORT); }
        public Builder mat4Field(int slot, String name) { return add(slot, name, ValueType.MAT4); }

        public Builder floatField(String name) { return add(name, ValueType.FLOAT); }
        public Builder vec2Field(String name) { return add(name, ValueType.VEC2F); }
        public Builder vec3Field(String name) { return add(name, ValueType.VEC3F); }
        public Builder vec4Field(String name) { return add(name, ValueType.VEC4F); }
        public Builder intField(String name) { return add(name, ValueType.INT); }
        public Builder vec2iField(String name) { return add(name, ValueType.VEC2I); }
        public Builder vec3iField(String name) { return add(name, ValueType.VEC3I); }
        public Builder vec4iField(String name) { return add(name, ValueType.VEC4I); }
        public Builder byteField(String name) { return add(name, ValueType.BYTE); }
        public Builder ubyteField(String name) { return add(name, ValueType.UBYTE); }
        public Builder shortField(String name) { return add(name, ValueType.SHORT); }
        public Builder ushortField(String name) { return add(name, ValueType.USHORT); }
        public Builder mat4Field(String name) { return add(name, ValueType.MAT4); }

        public Builder floatAttribute(int slot, String name) { return floatField(slot, name); }
        public Builder vec2Attribute(int slot, String name) { return vec2Field(slot, name); }
        public Builder vec3Attribute(int slot, String name) { return vec3Field(slot, name); }
        public Builder vec4Attribute(int slot, String name) { return vec4Field(slot, name); }
        public Builder intAttribute(int slot, String name) { return intField(slot, name); }
        public Builder vec2iAttribute(int slot, String name) { return vec2iField(slot, name); }
        public Builder vec3iAttribute(int slot, String name) { return vec3iField(slot, name); }
        public Builder vec4iAttribute(int slot, String name) { return vec4iField(slot, name); }
        public Builder byteAttribute(int slot, String name) { return byteField(slot, name); }
        public Builder ubyteAttribute(int slot, String name) { return ubyteField(slot, name); }
        public Builder shortAttribute(int slot, String name) { return shortField(slot, name); }
        public Builder ushortAttribute(int slot, String name) { return ushortField(slot, name); }
        public Builder mat4Attribute(int slot, String name) { return mat4Field(slot, name); }

        public Builder floatAttribute(String name) { return floatField(name); }
        public Builder vec2Attribute(String name) { return vec2Field(name); }
        public Builder vec3Attribute(String name) { return vec3Field(name); }
        public Builder vec4Attribute(String name) { return vec4Field(name); }
        public Builder intAttribute(String name) { return intField(name); }
        public Builder vec2iAttribute(String name) { return vec2iField(name); }
        public Builder vec3iAttribute(String name) { return vec3iField(name); }
        public Builder vec4iAttribute(String name) { return vec4iField(name); }
        public Builder byteAttribute(String name) { return byteField(name); }
        public Builder ubyteAttribute(String name) { return ubyteField(name); }
        public Builder shortAttribute(String name) { return shortField(name); }
        public Builder ushortAttribute(String name) { return ushortField(name); }
        public Builder mat4Attribute(String name) { return mat4Field(name); }

        public StructLayout build() {
            fields.sort((a, b) -> Integer.compare(a.getSlot(), b.getSlot()));
            return new StructLayout(name, fields);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StructLayout that)) {
            return false;
        }
        return stride == that.stride && Arrays.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(fields), stride);
    }

    @Override
    public String toString() {
        return "StructLayout{" +
                "name='" + name + '\'' +
                ", stride=" + stride +
                ", fields=" + Arrays.toString(fields) +
                '}';
    }
}

