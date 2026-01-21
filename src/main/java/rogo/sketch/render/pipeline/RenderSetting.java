package rogo.sketch.render.pipeline;

import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

import java.util.Objects;

public class RenderSetting {
    private final RenderParameter renderParameter;
    private final FullRenderState renderState;
    private final ResourceBinding resourceBinding;
    private final boolean shouldSwitchRenderState;

    public RenderSetting(RenderParameter renderParameter, PartialRenderSetting partialRenderSetting) {
        this.renderParameter = renderParameter == null ? RenderParameter.INVALID : renderParameter;
        this.renderState = partialRenderSetting.renderState();
        this.resourceBinding = partialRenderSetting.resourceBinding();
        this.shouldSwitchRenderState = partialRenderSetting.shouldSwitchRenderState();
    }

    public FullRenderState renderState() {
        return renderState;
    }

    public ResourceBinding resourceBinding() {
        return resourceBinding;
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
        return shouldSwitchRenderState == that.shouldSwitchRenderState && Objects.equals(renderState, that.renderState) && Objects.equals(resourceBinding, that.resourceBinding) && Objects.equals(renderParameter, that.renderParameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderState, resourceBinding, renderParameter, shouldSwitchRenderState);
    }

    public static RenderSetting fromPartial(RenderParameter renderParameter, PartialRenderSetting partial) {
        return new RenderSetting(renderParameter, partial);
    }

    public static RenderSetting computeShader(PartialRenderSetting partial) {
        return new RenderSetting(null, partial);
    }
}