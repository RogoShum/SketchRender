package rogo.sketch.vanilla.backend.opengl;

import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.driver.state.DynamicRenderState;
import rogo.sketch.core.driver.state.PassBindingState;
import rogo.sketch.core.driver.state.PipelineRasterState;
import rogo.sketch.core.pipeline.RenderContext;

final class LegacyMinecraftOpenGLStateApplier implements BackendStateApplier {
    private final BackendStateApplier delegate;

    LegacyMinecraftOpenGLStateApplier(BackendStateApplier delegate) {
        this.delegate = delegate;
    }

    @Override
    public void applyPipelineRasterState(PipelineRasterState state, RenderContext context) {
        delegate.applyPipelineRasterState(state, context);
    }

    @Override
    public void applyDynamicRenderState(DynamicRenderState state, RenderContext context) {
        delegate.applyDynamicRenderState(state, context);
    }

    @Override
    public void applyPassBindingState(PassBindingState state, RenderContext context) {
        delegate.applyPassBindingState(state, context);
    }
}
