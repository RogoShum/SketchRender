package rogo.sketch.core.data.layout;

import rogo.sketch.core.data.type.ValueType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Std430-compliant structured layout used for storage buffers.
 */
public class Std430StructLayout extends StructLayout {
    public Std430StructLayout(String name, FieldSpec... fields) {
        this(name, Arrays.asList(fields));
    }

    public Std430StructLayout(String name, List<FieldSpec> fields) {
        super(name, recalculateWithStd430(fields));
    }

    private static List<FieldSpec> recalculateWithStd430(List<FieldSpec> fields) {
        List<FieldSpec> result = new ArrayList<>(fields);
        int currentOffset = 0;
        for (FieldSpec field : result) {
            int alignment = getStd430Alignment(field.getValueType());
            int misalignment = currentOffset % alignment;
            if (misalignment != 0) {
                currentOffset += alignment - misalignment;
            }
            field.setOffset(currentOffset);
            currentOffset += field.getStride();
        }
        return result;
    }

    private static int getStd430Alignment(ValueType valueType) {
        return switch (valueType) {
            case FLOAT, INT, UINT -> 4;
            case VEC2F, VEC2I, VEC2UI -> 8;
            case VEC3F, VEC4F, VEC3I, VEC4I, VEC3UI, VEC4UI -> 16;
            case MAT2 -> 8;
            case MAT3, MAT4, MAT2X3, MAT2X4, MAT3X2, MAT3X4, MAT4X2, MAT4X3 -> 16;
            case BYTE, UBYTE -> 1;
            case SHORT, USHORT -> 2;
            case DOUBLE, VEC2D, VEC3D, VEC4D -> 8;
            default -> 4;
        };
    }

    public static Builder std430Builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<FieldSpec> fields = new ArrayList<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder element(String fieldName, ValueType valueType) {
            fields.add(new FieldSpec(fieldName, valueType, fields.size()));
            return this;
        }

        public Builder floatElement(String name) { return element(name, ValueType.FLOAT); }
        public Builder intElement(String name) { return element(name, ValueType.INT); }
        public Builder vec2Element(String name) { return element(name, ValueType.VEC2F); }
        public Builder vec3Element(String name) { return element(name, ValueType.VEC3F); }
        public Builder vec4Element(String name) { return element(name, ValueType.VEC4F); }
        public Builder mat4Element(String name) { return element(name, ValueType.MAT4); }

        public Std430StructLayout build() {
            return new Std430StructLayout(name, fields);
        }
    }

    public boolean validateStd430Layout() {
        int expectedOffset = 0;
        for (FieldSpec field : getFields()) {
            int alignment = getStd430Alignment(field.getValueType());
            int misalignment = expectedOffset % alignment;
            if (misalignment != 0) {
                expectedOffset += alignment - misalignment;
            }
            if (field.getOffset() != expectedOffset) {
                return false;
            }
            expectedOffset += field.getStride();
        }
        return true;
    }
}

