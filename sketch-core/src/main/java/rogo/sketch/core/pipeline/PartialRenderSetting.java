package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.driver.state.RenderStatePatch;

import java.util.Objects;

/**
 * Partial render setting for JSON loading (without render parameters)
 * Enhanced with automatic reload support using the generic reloadable system
 */
public class PartialRenderSetting implements ResourceObject {
    public static final PartialRenderSetting EMPTY = new PartialRenderSetting(RenderStatePatch.empty(), null, null, false);
    protected final RenderStatePatch renderState;
    protected final TargetBinding targetBinding;
    protected final ResourceBinding resourceBinding;
    protected final boolean shouldSwitchRenderState;
    private boolean disposed = false;

    public PartialRenderSetting(RenderStatePatch renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState) {
        this(renderState, null, resourceBinding, shouldSwitchRenderState);
    }

    public PartialRenderSetting(
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        this.renderState = renderState != null ? renderState : RenderStatePatch.empty();
        this.targetBinding = targetBinding != null ? targetBinding : TargetBinding.fromRenderState(renderState);
        this.resourceBinding = resourceBinding;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialRenderSetting that = (PartialRenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState
                && Objects.equals(renderState, that.renderState)
                && Objects.equals(targetBinding, that.targetBinding)
                && Objects.equals(resourceBinding, that.resourceBinding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderState, targetBinding, resourceBinding, shouldSwitchRenderState);
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public RenderStatePatch renderState() {
        return renderState;
    }

    public ResourceBinding resourceBinding() {
        return resourceBinding;
    }

    public TargetBinding targetBinding() {
        return targetBinding;
    }

    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    public static PartialRenderSetting create(RenderStatePatch renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState) {
        return new PartialRenderSetting(renderState, null, resourceBinding, shouldSwitchRenderState);
    }

    public static PartialRenderSetting create(
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        return new PartialRenderSetting(renderState, targetBinding, resourceBinding, shouldSwitchRenderState);
    }
}

