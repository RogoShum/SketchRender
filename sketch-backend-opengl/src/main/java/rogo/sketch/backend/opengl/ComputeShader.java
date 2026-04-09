package rogo.sketch.backend.opengl;

import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ComputeShader extends Shader implements rogo.sketch.core.shader.ComputeShader {
    public ComputeShader(GraphicsAPI api, KeyId keyId, String computeShaderSource) throws IOException {
        super(api, keyId, ShaderType.COMPUTE, computeShaderSource);
    }

    public ComputeShader(
            GraphicsAPI api,
            KeyId keyId,
            String computeShaderSource,
            ShaderPreprocessor preprocessor,
            Function<KeyId, Optional<InputStream>> resourceProvider,
            Map<String, String> macros,
            ShaderVertexLayout shaderVertexLayout) throws IOException {
        super(api, keyId, ShaderType.COMPUTE, computeShaderSource, preprocessor, resourceProvider, macros, shaderVertexLayout);
    }

    @Override
    protected void validateShaderTypes(Map<ShaderType, String> shaderSources) {
        if (!shaderSources.containsKey(ShaderType.COMPUTE)) {
            throw new IllegalArgumentException("Compute shader program requires a compute shader");
        }
        if (shaderSources.size() != 1) {
            throw new IllegalArgumentException("Compute shader program can only contain a compute shader");
        }
    }

    @Override
    public void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ) {
        api().dispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    @Override
    public void memoryBarrier(int barriers) {
        api().memoryBarrier(barriers);
    }
}

