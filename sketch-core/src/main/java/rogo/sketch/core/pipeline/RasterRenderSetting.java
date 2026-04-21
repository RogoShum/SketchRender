package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.resource.ResourceBinding;

public class RasterRenderSetting extends PartialRenderSetting {
    public RasterRenderSetting(
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        this(renderState, targetBinding, resourceBinding, shouldSwitchRenderState, null);
    }

    public RasterRenderSetting(
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState,
            String aliasPolicy) {
        super(ExecutionDomain.RASTER, renderState, targetBinding, resourceBinding, shouldSwitchRenderState, aliasPolicy);
    }
}
