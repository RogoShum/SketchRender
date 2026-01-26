package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.state.FullRenderState;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Partial render setting for JSON loading (without render parameters)
 * Enhanced with automatic reload support using the generic reloadable system
 */
public class PartialRenderSetting implements ResourceObject {
    protected final FullRenderState renderState;
    protected final ResourceBinding resourceBinding;
    protected final boolean shouldSwitchRenderState;
    private boolean disposed = false;

    public PartialRenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState) {
        this.renderState = renderState;
        this.resourceBinding = resourceBinding;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialRenderSetting that = (PartialRenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState && Objects.equals(renderState, that.renderState) && Objects.equals(resourceBinding, that.resourceBinding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderState, resourceBinding, shouldSwitchRenderState);
    }

    @Override
    public int getHandle() {
        return hashCode();
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public FullRenderState renderState() {
        return renderState;
    }

    public ResourceBinding resourceBinding() {
        return resourceBinding;
    }

    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    public static PartialRenderSetting create(FullRenderState renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState) {
        return new PartialRenderSetting(renderState, resourceBinding, shouldSwitchRenderState);
    }
}