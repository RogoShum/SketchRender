package rogo.sketch.core.pipeline;

import rogo.sketch.core.pipeline.parmeter.InvalidParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.driver.state.FullRenderState;

import java.util.Objects;

public class RenderSetting {
    private final RenderParameter renderParameter;
    private final FullRenderState renderState;
    private final TargetBinding targetBinding;
    private final ResourceBinding resourceBinding;
    private final boolean shouldSwitchRenderState;
    private final int hash;

    public RenderSetting(RenderParameter renderParameter, PartialRenderSetting partialRenderSetting) {
        this.renderParameter = renderParameter == null ? InvalidParameter.INVALID : renderParameter;
        this.renderState = partialRenderSetting.renderState();
        this.targetBinding = partialRenderSetting.targetBinding();
        this.resourceBinding = partialRenderSetting.resourceBinding();
        this.shouldSwitchRenderState = partialRenderSetting.shouldSwitchRenderState();
        this.hash = Objects.hash(renderParameter, partialRenderSetting, targetBinding, resourceBinding, shouldSwitchRenderState);
    }

    public FullRenderState renderState() {
        return renderState;
    }

    public ResourceBinding resourceBinding() {
        return resourceBinding;
    }

    public TargetBinding targetBinding() {
        return targetBinding;
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenderSetting that = (RenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState
                && Objects.equals(renderState, that.renderState)
                && Objects.equals(targetBinding, that.targetBinding)
                && Objects.equals(resourceBinding, that.resourceBinding)
                && Objects.equals(renderParameter, that.renderParameter);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public static RenderSetting fromPartial(RenderParameter renderParameter, PartialRenderSetting partial) {
        return new RenderSetting(renderParameter, partial);
    }
}
