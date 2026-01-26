package rogo.sketch.core.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

/**
 * Enumeration of supported shader types
 */
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER, "vert"),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER, "frag"),
    GEOMETRY(GL32.GL_GEOMETRY_SHADER, "geom"),
    TESS_CONTROL(GL40.GL_TESS_CONTROL_SHADER, "tesc"),
    TESS_EVALUATION(GL40.GL_TESS_EVALUATION_SHADER, "tese"),
    COMPUTE(GL43.GL_COMPUTE_SHADER, "comp");

    private final int glType;
    private final String extension;

    ShaderType(int glType, String extension) {
        this.glType = glType;
        this.extension = extension;
    }

    public int getGLType() {
        return glType;
    }

    public String getExtension() {
        return extension;
    }

    /**
     * Get shader type by file extension
     */
    public static ShaderType fromExtension(String extension) {
        for (ShaderType type : values()) {
            if (type.extension.equals(extension)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown shader extension: " + extension);
    }

    /**
     * Check if this is a graphics pipeline shader type
     */
    public boolean isGraphicsShader() {
        return this != COMPUTE;
    }

    /**
     * Check if this shader type is required for graphics pipeline
     */
    public boolean isRequired() {
        return this == VERTEX || this == FRAGMENT;
    }
}