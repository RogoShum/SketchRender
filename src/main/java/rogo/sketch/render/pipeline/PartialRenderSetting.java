package rogo.sketch.render.pipeline;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.util.KeyId;

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

    private final KeyId sourceKeyId;

    public PartialRenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState, KeyId sourceKeyId) {
        this.renderState = renderState;
        this.resourceBinding = resourceBinding;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.sourceKeyId = sourceKeyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialRenderSetting that = (PartialRenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState && Objects.equals(renderState, that.renderState) && Objects.equals(resourceBinding, that.resourceBinding) && Objects.equals(sourceKeyId, that.sourceKeyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderState, resourceBinding, shouldSwitchRenderState, sourceKeyId);
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

    /**
     * Create a reloadable partial render setting with source tracking
     */
    public static PartialRenderSetting reloadable(FullRenderState renderState, ResourceBinding resourceBinding,
                                                  boolean shouldSwitchRenderState, KeyId sourceKeyId) {
        return new PartialRenderSetting(renderState, resourceBinding, shouldSwitchRenderState, sourceKeyId);
    }

    /**
     * Get the source identifier for this setting
     */
    public KeyId getSourceIdentifier() {
        return sourceKeyId;
    }

    /**
     * Check if this setting supports automatic reloading
     */
    public boolean isReloadable() {
        return sourceKeyId != null;
    }
}