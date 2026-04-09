package rogo.sketch.core.shader;
import rogo.sketch.core.api.graphics.ComputeDispatchContext;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.uniform.UniformHookGroup;

public final class ComputeDispatchSupport {
    private ComputeDispatchSupport() {
    }

    public static ComputeDispatchContext createContext(RenderContext renderContext) {
        ShaderProgramHandle programHandle = renderContext != null
                ? renderContext.shaderProgramHandle()
                : ShaderProgramResolver.adaptProgramHandle(null);
        UniformHookGroup uniformHookGroup = programHandle != null
                ? programHandle.uniformHooks()
                : new UniformHookGroup();
        ComputeShader computeShaderAdapter = programHandle != null ? programHandle.computeShaderAdapter() : null;

        return new ComputeDispatchContext() {
            @Override
            public RenderContext renderContext() {
                return renderContext;
            }

            @Override
            public ShaderProgramHandle programHandle() {
                return programHandle;
            }

            @Override
            public UniformHookGroup uniformHookGroup() {
                return uniformHookGroup;
            }

            @Override
            public void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ) {
                requireComputeShaderAdapter(programHandle, computeShaderAdapter)
                        .dispatch(numGroupsX, numGroupsY, numGroupsZ);
            }

            @Override
            public void memoryBarrier(int barriers) {
                requireComputeShaderAdapter(programHandle, computeShaderAdapter)
                        .memoryBarrier(barriers);
            }

            @Override
            public void shaderStorageBarrier() {
                requireComputeShaderAdapter(programHandle, computeShaderAdapter)
                        .shaderStorageBarrier();
            }

            @Override
            public void allBarriers() {
                requireComputeShaderAdapter(programHandle, computeShaderAdapter)
                        .allBarriers();
            }
        };
    }

    private static ComputeShader requireComputeShaderAdapter(
            ShaderProgramHandle programHandle,
            ComputeShader computeShaderAdapter) {
        if (computeShaderAdapter != null) {
            return computeShaderAdapter;
        }
        String programLabel = programHandle != null
                ? programHandle.templateId() + "/" + programHandle.variantKey()
                : "unknown_program";
        throw new IllegalStateException(
                "No backend compute shader adapter is available for " + programLabel);
    }
}

