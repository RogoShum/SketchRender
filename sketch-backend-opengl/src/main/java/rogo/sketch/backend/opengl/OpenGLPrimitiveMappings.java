package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL11;
import rogo.sketch.core.data.PrimitiveType;

public final class OpenGLPrimitiveMappings {
    private OpenGLPrimitiveMappings() {
    }

    public static int toGlType(PrimitiveType primitiveType) {
        if (primitiveType == null) {
            return GL11.GL_TRIANGLES;
        }
        return switch (primitiveType) {
            case POINTS -> GL11.GL_POINTS;
            case LINES -> GL11.GL_LINES;
            case LINE_STRIP -> GL11.GL_LINE_STRIP;
            case LINE_LOOP -> GL11.GL_LINE_LOOP;
            case TRIANGLES, QUADS -> GL11.GL_TRIANGLES;
            case TRIANGLE_STRIP -> GL11.GL_TRIANGLE_STRIP;
            case TRIANGLE_FAN -> GL11.GL_TRIANGLE_FAN;
        };
    }
}
