package rogo.sketch.core.driver.state;

import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.component.ShaderState;

/**
 * Runtime pass/program-facing state bindings.
 */
public record PassBindingState(
        RenderTargetState renderTargetState,
        ShaderState shaderState
) {
}

