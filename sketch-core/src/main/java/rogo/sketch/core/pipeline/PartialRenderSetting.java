package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.driver.state.RenderStatePatch;

import java.util.Objects;

/**
 * Partial render setting for JSON loading (without render parameters)
 * Enhanced with automatic reload support using the generic reloadable system
 */
public class PartialRenderSetting implements ResourceObject {
    public static final PartialRenderSetting EMPTY = new PartialRenderSetting(
            ExecutionDomain.RASTER,
            RenderStatePatch.empty(),
            null,
            null,
            false,
            null);
    protected final ExecutionDomain executionDomain;
    protected final RenderStatePatch renderState;
    protected final TargetBinding targetBinding;
    protected final ResourceBinding resourceBinding;
    protected final boolean shouldSwitchRenderState;
    protected final String aliasPolicy;
    private boolean disposed = false;

    public PartialRenderSetting(RenderStatePatch renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState) {
        this(ExecutionDomain.RASTER, renderState, null, resourceBinding, shouldSwitchRenderState);
    }

    public PartialRenderSetting(
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        this(ExecutionDomain.RASTER, renderState, targetBinding, resourceBinding, shouldSwitchRenderState);
    }

    protected PartialRenderSetting(
            ExecutionDomain executionDomain,
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        this(executionDomain, renderState, targetBinding, resourceBinding, shouldSwitchRenderState, null);
    }

    protected PartialRenderSetting(
            ExecutionDomain executionDomain,
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState,
            String aliasPolicy) {
        this.executionDomain = executionDomain != null ? executionDomain : ExecutionDomain.RASTER;
        this.renderState = renderState != null ? renderState : RenderStatePatch.empty();
        this.targetBinding = targetBinding != null ? targetBinding : TargetBinding.fromRenderState(renderState);
        this.resourceBinding = resourceBinding;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.aliasPolicy = aliasPolicy != null && !aliasPolicy.isBlank() ? aliasPolicy : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialRenderSetting that = (PartialRenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState
                && executionDomain == that.executionDomain
                && Objects.equals(renderState, that.renderState)
                && Objects.equals(targetBinding, that.targetBinding)
                && Objects.equals(resourceBinding, that.resourceBinding)
                && Objects.equals(aliasPolicy, that.aliasPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionDomain, renderState, targetBinding, resourceBinding, shouldSwitchRenderState, aliasPolicy);
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

    public ExecutionDomain executionDomain() {
        return executionDomain;
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

    public String aliasPolicy() {
        return aliasPolicy;
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

    public static PartialRenderSetting create(
            ExecutionDomain executionDomain,
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        return create(executionDomain, renderState, targetBinding, resourceBinding, shouldSwitchRenderState, null);
    }

    public static PartialRenderSetting create(
            ExecutionDomain executionDomain,
            RenderStatePatch renderState,
            TargetBinding targetBinding,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState,
            String aliasPolicy) {
        return switch (executionDomain != null ? executionDomain : ExecutionDomain.RASTER) {
            case COMPUTE -> new ComputeRenderSetting(renderState, resourceBinding, shouldSwitchRenderState, aliasPolicy);
            case OFFSCREEN_GRAPHICS ->
                    new OffscreenGraphicsRenderSetting(renderState, targetBinding, resourceBinding, shouldSwitchRenderState, aliasPolicy);
            case TRANSFER -> new TransferSetting(resourceBinding, shouldSwitchRenderState, aliasPolicy);
            case RASTER -> new RasterRenderSetting(renderState, targetBinding, resourceBinding, shouldSwitchRenderState, aliasPolicy);
        };
    }
}

