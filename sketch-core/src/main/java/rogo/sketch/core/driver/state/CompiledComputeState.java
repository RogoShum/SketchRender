package rogo.sketch.core.driver.state;

public record CompiledComputeState(
        RenderStatePatch patch,
        ShaderBindingState shaderBindingState
) {
    public static CompiledComputeState empty() {
        return ComputeStateCompiler.compile(RenderStatePatch.empty());
    }
}
