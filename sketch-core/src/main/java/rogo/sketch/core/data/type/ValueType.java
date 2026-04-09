package rogo.sketch.core.data.type;

import java.util.HashMap;
import java.util.Map;

public enum ValueType {
    FLOAT(ScalarType.FLOAT32, ValueShape.SCALAR),
    VEC2F(ScalarType.FLOAT32, ValueShape.VEC2),
    VEC3F(ScalarType.FLOAT32, ValueShape.VEC3),
    VEC4F(ScalarType.FLOAT32, ValueShape.VEC4),

    INT(ScalarType.SINT32, ValueShape.SCALAR),
    VEC2I(ScalarType.SINT32, ValueShape.VEC2),
    VEC3I(ScalarType.SINT32, ValueShape.VEC3),
    VEC4I(ScalarType.SINT32, ValueShape.VEC4),

    UINT(ScalarType.UINT32, ValueShape.SCALAR),
    VEC2UI(ScalarType.UINT32, ValueShape.VEC2),
    VEC3UI(ScalarType.UINT32, ValueShape.VEC3),
    VEC4UI(ScalarType.UINT32, ValueShape.VEC4),

    BYTE(ScalarType.SINT8, ValueShape.SCALAR),
    VEC2B(ScalarType.SINT8, ValueShape.VEC2),
    VEC3B(ScalarType.SINT8, ValueShape.VEC3),
    VEC4B(ScalarType.SINT8, ValueShape.VEC4),

    UBYTE(ScalarType.UINT8, ValueShape.SCALAR),
    VEC2UB(ScalarType.UINT8, ValueShape.VEC2),
    VEC3UB(ScalarType.UINT8, ValueShape.VEC3),
    VEC4UB(ScalarType.UINT8, ValueShape.VEC4),

    SHORT(ScalarType.SINT16, ValueShape.SCALAR),
    VEC2S(ScalarType.SINT16, ValueShape.VEC2),
    VEC3S(ScalarType.SINT16, ValueShape.VEC3),
    VEC4S(ScalarType.SINT16, ValueShape.VEC4),

    USHORT(ScalarType.UINT16, ValueShape.SCALAR),
    VEC2US(ScalarType.UINT16, ValueShape.VEC2),
    VEC3US(ScalarType.UINT16, ValueShape.VEC3),
    VEC4US(ScalarType.UINT16, ValueShape.VEC4),

    DOUBLE(ScalarType.FLOAT64, ValueShape.SCALAR),
    VEC2D(ScalarType.FLOAT64, ValueShape.VEC2),
    VEC3D(ScalarType.FLOAT64, ValueShape.VEC3),
    VEC4D(ScalarType.FLOAT64, ValueShape.VEC4),

    MAT2(ScalarType.FLOAT32, ValueShape.MAT2),
    MAT3(ScalarType.FLOAT32, ValueShape.MAT3),
    MAT4(ScalarType.FLOAT32, ValueShape.MAT4),
    MAT2X3(ScalarType.FLOAT32, ValueShape.MAT2X3),
    MAT2X4(ScalarType.FLOAT32, ValueShape.MAT2X4),
    MAT3X2(ScalarType.FLOAT32, ValueShape.MAT3X2),
    MAT3X4(ScalarType.FLOAT32, ValueShape.MAT3X4),
    MAT4X2(ScalarType.FLOAT32, ValueShape.MAT4X2),
    MAT4X3(ScalarType.FLOAT32, ValueShape.MAT4X3);

    private static final Map<String, ValueType> NAME_LOOKUP = new HashMap<>();

    static {
        for (ValueType type : values()) {
            NAME_LOOKUP.put(type.name().toLowerCase(), type);
        }
    }

    private final ScalarType scalarType;
    private final ValueShape shape;
    private final int byteSize;
    private final int componentCount;
    private final int componentByteSize;

    ValueType(ScalarType scalarType, ValueShape shape) {
        this.scalarType = scalarType;
        this.shape = shape;
        this.componentCount = shape.componentCount();
        this.componentByteSize = scalarType.byteSize();
        this.byteSize = componentCount * componentByteSize;
    }

    public ScalarType scalarType() {
        return scalarType;
    }

    public ValueShape shape() {
        return shape;
    }

    public int componentCount() {
        return componentCount;
    }

    public int getComponentCount() {
        return componentCount();
    }

    public int byteSize() {
        return byteSize;
    }

    public int getStride() {
        return byteSize();
    }

    public int componentByteSize() {
        return componentByteSize;
    }

    public int getComponentSize() {
        return componentByteSize();
    }

    public boolean isFloatingPoint() {
        return scalarType.isFloatingPoint();
    }

    public boolean isFloatType() {
        return isFloatingPoint() && scalarType == ScalarType.FLOAT32;
    }

    public boolean isIntegerType() {
        return scalarType.isSignedInteger() || scalarType.isUnsignedInteger();
    }

    public boolean isByteType() {
        return scalarType == ScalarType.SINT8 || scalarType == ScalarType.UINT8;
    }

    public boolean isShortType() {
        return scalarType == ScalarType.SINT16 || scalarType == ScalarType.UINT16;
    }

    public boolean isDoubleType() {
        return scalarType == ScalarType.FLOAT64;
    }

    public boolean isMatrixType() {
        return shape.isMatrix();
    }

    public boolean isVectorType() {
        return shape.isVector();
    }

    public boolean isScalarType() {
        return shape.isScalar();
    }

    public boolean isUnsigned() {
        return scalarType.isUnsignedInteger();
    }

    public boolean isCompatibleWith(ValueType other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if (scalarType == other.scalarType && componentCount == other.componentCount) {
            return true;
        }
        return isFloatingPoint() && other.isIntegerType() && componentCount == other.componentCount;
    }

    public static ValueType getByName(String name) {
        return name == null ? null : NAME_LOOKUP.get(name.toLowerCase());
    }
}

