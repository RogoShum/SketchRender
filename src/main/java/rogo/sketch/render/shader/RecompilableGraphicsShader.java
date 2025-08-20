package rogo.sketch.render.shader;

import rogo.sketch.render.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.render.shader.preprocessor.ShaderResourceProvider;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Map;

/**
 * Graphics shader with recompilation support
 */
public class RecompilableGraphicsShader extends RecompilableShader {
    
    public RecompilableGraphicsShader(Identifier identifier,
                                     Map<ShaderType, String> shaderSources,
                                     ShaderPreprocessor preprocessor,
                                     ShaderResourceProvider resourceProvider) throws IOException {
        super(identifier, shaderSources, preprocessor, resourceProvider);
    }
    
    @Override
    protected void validateShaderTypes(Map<ShaderType, String> shaderSources) {
        // Ensure required shader types are present
        if (!shaderSources.containsKey(ShaderType.VERTEX)) {
            throw new IllegalArgumentException("Graphics shader must have a vertex shader");
        }
        if (!shaderSources.containsKey(ShaderType.FRAGMENT)) {
            throw new IllegalArgumentException("Graphics shader must have a fragment shader");
        }
        
        // Ensure no compute shader is present
        if (shaderSources.containsKey(ShaderType.COMPUTE)) {
            throw new IllegalArgumentException("Graphics shader cannot contain compute shader");
        }
    }
    
    /**
     * Convenient method to create a basic vertex/fragment shader
     */
    public static RecompilableGraphicsShader create(Identifier identifier,
                                                   String vertexSource,
                                                   String fragmentSource,
                                                   ShaderPreprocessor preprocessor,
                                                   ShaderResourceProvider resourceProvider) throws IOException {
        Map<ShaderType, String> sources = Map.of(
            ShaderType.VERTEX, vertexSource,
            ShaderType.FRAGMENT, fragmentSource
        );
        return new RecompilableGraphicsShader(identifier, sources, preprocessor, resourceProvider);
    }
}
