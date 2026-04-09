package rogo.sketch.core.driver.state;

import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.component.ShaderState;

/**
 * Fully compiled runtime state derived from a sparse authoring patch and defaults.
 */
public record CompiledRenderState(
        RenderStatePatch patch,
        PipelineRasterState pipelineRasterState,
        DynamicRenderState dynamicRenderState,
        PassBindingState passBindingState
) {
    public static CompiledRenderState empty() {
        return RenderStateCompiler.compile(RenderStatePatch.empty());
    }

    public ShaderState shaderState() {
        return passBindingState != null ? passBindingState.shaderState() : null;
    }

    public RenderTargetState renderTargetState() {
        return passBindingState != null ? passBindingState.renderTargetState() : null;
    }
}

