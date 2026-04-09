package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL11;
import rogo.sketch.core.data.IndexType;

public final class OpenGLIndexTypeMappings {
    private OpenGLIndexTypeMappings() {
    }

    public static int toGlType(IndexType indexType) {
        if (indexType == null) {
            return GL11.GL_UNSIGNED_INT;
        }
        return switch (indexType) {
            case U_BYTE -> GL11.GL_UNSIGNED_BYTE;
            case U_SHORT -> GL11.GL_UNSIGNED_SHORT;
            case U_INT -> GL11.GL_UNSIGNED_INT;
        };
    }
}
