package rogo.sketchrender.uniform;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_FLOAT;

public enum DataType {
    FLOAT(GL_FLOAT, Float.BYTES),
    VEC2(GL_FLOAT, 2 * Float.BYTES),
    VEC3(GL_FLOAT, 3 * Float.BYTES),
    VEC4(GL_FLOAT, 4 * Float.BYTES),
    MAT4(GL_FLOAT, 16 * Float.BYTES);

    private static final Map<Integer, DataType> lookup = new HashMap<>();
    private final int glType;
    private final int stride;

    static {
        for (DataType type : DataType.values()) {
            lookup.put(type.getGLType(), type);
        }
    }

    DataType(int glType, int stride) {
        this.glType = glType;
        this.stride = stride;
    }

    public int getGLType() {
        return glType;
    }

    public int getStride() {
        return stride;
    }

    public static DataType getByGLType(int glType) {
        return lookup.get(glType);
    }
}