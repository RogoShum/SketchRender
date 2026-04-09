package rogo.sketch.core.shader;

/**
 * Enumeration of supported shader types
 */
public enum ShaderType {
    VERTEX("vert"),
    FRAGMENT("frag"),
    GEOMETRY("geom"),
    TESS_CONTROL("tesc"),
    TESS_EVALUATION("tese"),
    COMPUTE("comp");

    private final String extension;

    ShaderType(String extension) {
        this.extension = extension;
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
