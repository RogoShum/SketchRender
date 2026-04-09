package rogo.sketch.core.backend;

import rogo.sketch.core.driver.state.DynamicRenderState;
import rogo.sketch.core.driver.state.PassBindingState;
import rogo.sketch.core.driver.state.PipelineRasterState;
import rogo.sketch.core.pipeline.RenderContext;

/**
 * Backend-owned render-state applicator.
 */
public interface BackendStateApplier {
    BackendStateApplier NO_OP = new BackendStateApplier() {
    };

    default void applyPipelineRasterState(PipelineRasterState state, RenderContext context) {
    }

    default void applyDynamicRenderState(DynamicRenderState state, RenderContext context) {
    }

    default void applyPassBindingState(PassBindingState state, RenderContext context) {
    }
}


