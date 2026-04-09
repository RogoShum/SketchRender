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
    public static final KeyId DEFAULT_RENDER_TARGET = KeyId.of("minecraft:main_target");
    private static final TargetBinding DEFAULT = new TargetBinding(DEFAULT_RENDER_TARGET, List.of(), null, null);

    public TargetBinding {
        renderTargetId = renderTargetId != null ? renderTargetId : DEFAULT_RENDER_TARGET;
        drawBuffers = drawBuffers != null ? List.copyOf(drawBuffers) : List.of();
    }

    public static TargetBinding defaultTarget() {
        return DEFAULT;
    }

    public static TargetBinding fromRenderState(RenderStatePatch renderState) {
        if (renderState == null || renderState.isEmpty()) {
            return defaultTarget();
        }
        Object state = renderState.get(RenderTargetState.TYPE);
        if (state instanceof RenderTargetState renderTargetState) {
            return renderTargetState.toTargetBinding();
        }
        return defaultTarget();
    }

    public boolean isDefaultTarget() {
        return DEFAULT_RENDER_TARGET.equals(renderTargetId) && drawBuffers.isEmpty();
    }
}

