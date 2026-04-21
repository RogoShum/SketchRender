package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.resource.ResourceBinding;

public class OffscreenGraphicsRenderSetting extends PartialRenderSetting {
    public OffscreenGraphicsRenderSetting(
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        this(renderState, targetBinding, resourceBinding, shouldSwitchRenderState, null);
    }

    public OffscreenGraphicsRenderSetting(
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState,
            String aliasPolicy) {
        super(ExecutionDomain.OFFSCREEN_GRAPHICS, renderState, targetBinding, resourceBinding, shouldSwitchRenderState, aliasPolicy);
        if (targetBinding == null || TargetBinding.DEFAULT_RENDER_TARGET.equals(targetBinding.renderTargetId())) {
            throw new IllegalArgumentException("OffscreenGraphicsRenderSetting requires a backend-owned offscreen target");
        }
    }
}
