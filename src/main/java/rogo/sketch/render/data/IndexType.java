package rogo.sketch.render.data;

import org.lwjgl.opengl.GL15;
import rogo.sketch.render.resource.buffer.IndexBufferResource;

public enum IndexType {
    U_BYTE(GL15.GL_UNSIGNED_BYTE, 1),
    U_SHORT(GL15.GL_UNSIGNED_SHORT, 2),
    U_INT(GL15.GL_UNSIGNED_INT, 4);

    private final int glType;
    private final int bytes;

    IndexType(int glType, int bytes) {
        this.glType = glType;
        this.bytes = bytes;
    }

    public int glType() {
        return glType;
    }

    public int bytes() {
        return bytes;
    }

    public static IndexType getOptimalType(int maxIndex) {
        if (maxIndex < 256) {
            return U_BYTE;
        } else if (maxIndex < 65536) {
            return U_SHORT;
        } else {
            return U_INT;
        }
    }
}
