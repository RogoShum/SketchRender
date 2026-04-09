package rogo.sketch.core.data.layout;

import rogo.sketch.core.data.type.ValueType;

import java.util.Objects;

/**
 * Describes a single field within a structured layout.
 */
public class FieldSpec {
    private final String name;
    private final ValueType valueType;
    private final int slot;
    private final boolean normalized;
    private final boolean sortKey;
    private final boolean padding;
    private int offset;

    public FieldSpec(String name, ValueType valueType, int slot) {
        this(name, valueType, slot, false, false, false);
    }

    public FieldSpec(String name, ValueType valueType, int slot, boolean normalized, boolean sortKey, boolean padding) {
        this.name = name;
        this.valueType = valueType;
        this.slot = slot;
        this.normalized = normalized;
        this.sortKey = sortKey;
        this.padding = padding;
    }

    public String getName() {
        return name;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public ValueType getDataType() {
        return valueType;
    }

    public int getSlot() {
        return slot;
    }

    public int getIndex() {
        return slot;
    }

    public boolean isNormalized() {
        return normalized;
    }

    public boolean isSortKey() {
        return sortKey;
    }

    public boolean isPadding() {
        return padding;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getComponentCount() {
        return valueType.componentCount();
    }

    public int getStride() {
        return valueType.byteSize();
    }

    public int getComponentSize() {
        return valueType.componentByteSize();
    }

    public FieldSpec copy(int slot) {
        return new FieldSpec(name, valueType, slot, normalized, sortKey, padding);
    }

    public boolean isCompatibleWith(FieldSpec other) {
        return other != null
                && valueType.isCompatibleWith(other.valueType)
                && slot == other.slot
                && normalized == other.normalized
                && padding == other.padding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldSpec fieldSpec)) {
            return false;
        }
        return slot == fieldSpec.slot
                && normalized == fieldSpec.normalized
                && sortKey == fieldSpec.sortKey
                && padding == fieldSpec.padding
                && offset == fieldSpec.offset
                && Objects.equals(name, fieldSpec.name)
                && valueType == fieldSpec.valueType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, valueType, slot, normalized, sortKey, padding, offset);
    }

    @Override
    public String toString() {
        return "FieldSpec{" +
                "name='" + name + '\'' +
                ", valueType=" + valueType +
                ", slot=" + slot +
                ", normalized=" + normalized +
                ", offset=" + offset +
                ", sortKey=" + sortKey +
                ", padding=" + padding +
                '}';
    }
}

