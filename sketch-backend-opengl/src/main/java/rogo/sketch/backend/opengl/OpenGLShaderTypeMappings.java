package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import rogo.sketch.core.shader.ShaderType;

public final class OpenGLShaderTypeMappings {
    private OpenGLShaderTypeMappings() {
    }

    public static int toGlType(ShaderType shaderType) {
        if (shaderType == null) {
            throw new IllegalArgumentException("shaderType cannot be null");
        }
        return switch (shaderType) {
            case VERTEX -> GL20.GL_VERTEX_SHADER;
            case FRAGMENT -> GL20.GL_FRAGMENT_SHADER;
            case GEOMETRY -> GL32.GL_GEOMETRY_SHADER;
            case TESS_CONTROL -> GL40.GL_TESS_CONTROL_SHADER;
            case TESS_EVALUATION -> GL40.GL_TESS_EVALUATION_SHADER;
            case COMPUTE -> GL43.GL_COMPUTE_SHADER;
        };
    }
}
