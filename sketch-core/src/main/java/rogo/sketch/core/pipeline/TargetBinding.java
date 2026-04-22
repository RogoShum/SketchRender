package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record TargetBinding(
        KeyId renderTargetId,
        List<Object> drawBuffers,
        Boolean clearColor,
        Boolean clearDepth
) {
    public TargetBinding {
        renderTargetId = renderTargetId != null ? renderTargetId : PipelineConfig.DEFAULT_RENDER_TARGET_ID;
        drawBuffers = drawBuffers != null ? List.copyOf(drawBuffers) : List.of();
    }

    public static TargetBinding fromRenderState(RenderStatePatch renderState) {
        if (renderState == null || renderState.isEmpty()) {
            return new TargetBinding(PipelineConfig.DEFAULT_RENDER_TARGET_ID, List.of(), null, null);
        }
        Object state = renderState.get(RenderTargetState.TYPE);
        if (state instanceof RenderTargetState renderTargetState) {
            return renderTargetState.toTargetBinding();
        }
        return new TargetBinding(PipelineConfig.DEFAULT_RENDER_TARGET_ID, List.of(), null, null);
    }

    public boolean isDefaultTarget() {
        return PipelineConfig.DEFAULT_RENDER_TARGET_ID.equals(renderTargetId) && drawBuffers.isEmpty();
    }
}

