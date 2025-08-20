package rogo.sketch.render.shader;

import rogo.sketch.render.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.render.shader.preprocessor.ShaderResourceProvider;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Map;

/**
 * Compute shader with recompilation support
 */
public class RecompilableComputeShader extends RecompilableShader {
    
    public RecompilableComputeShader(Identifier identifier,
                                    String computeSource,
                                    ShaderPreprocessor preprocessor,
                                    ShaderResourceProvider resourceProvider) throws IOException {
        super(identifier, ShaderType.COMPUTE, computeSource, preprocessor, resourceProvider);
    }
    
    @Override
    protected void validateShaderTypes(Map<ShaderType, String> shaderSources) {
        // Ensure only compute shader is present
        if (shaderSources.size() != 1 || !shaderSources.containsKey(ShaderType.COMPUTE)) {
            throw new IllegalArgumentException("Compute shader must contain exactly one compute shader stage");
        }
    }
}
