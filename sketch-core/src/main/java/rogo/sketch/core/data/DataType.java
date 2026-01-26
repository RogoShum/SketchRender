package rogo.sketch.core.data;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public enum DataType {
    // Float types
    FLOAT(GL_FLOAT, 1, Float.BYTES),
    VEC2F(GL_FLOAT, 2, 2 * Float.BYTES),
    VEC3F(GL_FLOAT, 3, 3 * Float.BYTES),
    VEC4F(GL_FLOAT, 4, 4 * Float.BYTES),

    // Integer types
    INT(GL_INT, 1, Integer.BYTES),
    VEC2I(GL_INT, 2, 2 * Integer.BYTES),
    VEC3I(GL_INT, 3, 3 * Integer.BYTES),
    VEC4I(GL_INT, 4, 4 * Integer.BYTES),

    // Unsigned integer types (GLSL: uint, uvec2, uvec3, uvec4)
    UINT(GL_UNSIGNED_INT, 1, Integer.BYTES),
    VEC2UI(GL_UNSIGNED_INT, 2, 2 * Integer.BYTES),
    VEC3UI(GL_UNSIGNED_INT, 3, 3 * Integer.BYTES),
    VEC4UI(GL_UNSIGNED_INT, 4, 4 * Integer.BYTES),

    // Byte types
    BYTE(GL_BYTE, 1, Byte.BYTES),
    VEC2B(GL_BYTE, 2, 2 * Byte.BYTES),
    VEC3B(GL_BYTE, 3, 3 * Byte.BYTES),
    VEC4B(GL_BYTE, 4, 4 * Byte.BYTES),

    // Unsigned byte types
    UBYTE(GL_UNSIGNED_BYTE, 1, Byte.BYTES),
    VEC2UB(GL_UNSIGNED_BYTE, 2, 2 * Byte.BYTES),
    VEC3UB(GL_UNSIGNED_BYTE, 3, 3 * Byte.BYTES),
    VEC4UB(GL_UNSIGNED_BYTE, 4, 4 * Byte.BYTES),

    // Short types
    SHORT(GL_SHORT, 1, Short.BYTES),
    VEC2S(GL_SHORT, 2, 2 * Short.BYTES),
    VEC3S(GL_SHORT, 3, 3 * Short.BYTES),
    VEC4S(GL_SHORT, 4, 4 * Short.BYTES),

    // Unsigned short types
    USHORT(GL_UNSIGNED_SHORT, 1, Short.BYTES),
    VEC2US(GL_UNSIGNED_SHORT, 2, 2 * Short.BYTES),
    VEC3US(GL_UNSIGNED_SHORT, 3, 3 * Short.BYTES),
    VEC4US(GL_UNSIGNED_SHORT, 4, 4 * Short.BYTES),

    // Double types
    DOUBLE(GL_DOUBLE, 1, Double.BYTES),
    VEC2D(GL_DOUBLE, 2, 2 * Double.BYTES),
    VEC3D(GL_DOUBLE, 3, 3 * Double.BYTES),
    VEC4D(GL_DOUBLE, 4, 4 * Double.BYTES),

    // Matrix types
    MAT2(GL_FLOAT, 4, 4 * Float.BYTES),
    MAT3(GL_FLOAT, 9, 9 * Float.BYTES),
    MAT4(GL_FLOAT, 16, 16 * Float.BYTES),
    MAT2X3(GL_FLOAT, 6, 6 * Float.BYTES),
    MAT2X4(GL_FLOAT, 8, 8 * Float.BYTES),
    MAT3X2(GL_FLOAT, 6, 6 * Float.BYTES),
    MAT3X4(GL_FLOAT, 12, 12 * Float.BYTES),
    MAT4X2(GL_FLOAT, 8, 8 * Float.BYTES),
    MAT4X3(GL_FLOAT, 12, 12 * Float.BYTES);

    private static final Map<Integer, DataType> lookup = new HashMap<>();
    private static final Map<String, DataType> nameLookup = new HashMap<>();
    private final int glType;
    private final int componentCount;
    private final int stride;

    static {
        for (DataType type : DataType.values()) {
            lookup.put(type.getGLType(), type);
            nameLookup.put(type.name().toLowerCase(), type);
        }
    }

    DataType(int glType, int componentCount, int stride) {
        this.glType = glType;
        this.componentCount = componentCount;
        this.stride = stride;
    }

    public int getGLType() {
        return glType;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public int getStride() {
        return stride;
    }

    public int getComponentSize() {
        return stride / componentCount;
    }

    public boolean isFloatType() {
        return glType == GL_FLOAT;
    }

    public boolean isIntegerType() {
        return glType == GL_INT || glType == GL_UNSIGNED_INT;
    }

    public boolean isByteType() {
        return glType == GL_BYTE || glType == GL_UNSIGNED_BYTE;
    }

    public boolean isShortType() {
        return glType == GL_SHORT || glType == GL_UNSIGNED_SHORT;
    }

    public boolean isDoubleType() {
        return glType == GL_DOUBLE;
    }

    public boolean isMatrixType() {
        return name().startsWith("MAT");
    }

    public boolean isVectorType() {
        return name().startsWith("VEC") && !isMatrixType();
    }

    public boolean isScalarType() {
        return componentCount == 1;
    }

    public boolean isUnsigned() {
        return glType == GL_UNSIGNED_INT || glType == GL_UNSIGNED_BYTE || glType == GL_UNSIGNED_SHORT;
    }

    public static DataType getByGLType(int glType) {
        return lookup.get(glType);
    }

    public static DataType getByName(String name) {
        return nameLookup.get(name.toLowerCase());
    }

    /**
     * Check if two data types are compatible for vertex attribute matching
     */
    public boolean isCompatibleWith(DataType other) {
        if (this == other) return true;

        // Same GL type and component count
        if (this.glType == other.glType && this.componentCount == other.componentCount) {
            return true;
        }

        // Float types can be compatible with normalized integer types
        if (this.isFloatType() && other.isIntegerType() && this.componentCount == other.componentCount) {
            return true;
        }

        return false;
    }
}