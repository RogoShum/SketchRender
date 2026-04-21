package rogo.sketch.core.driver.state;

import rogo.sketch.core.driver.state.component.ShaderState;

public final class ComputeStateCompiler {
    private ComputeStateCompiler() {
    }

    public static CompiledComputeState compile(RenderStatePatch patch) {
        RenderStatePatch resolvedPatch = patch != null ? patch : RenderStatePatch.empty();
        ShaderState shaderState = resolvedPatch.get(ShaderState.TYPE) instanceof ShaderState state ? state : new ShaderState();
        return new CompiledComputeState(resolvedPatch, new ShaderBindingState(shaderState));
    }
}
