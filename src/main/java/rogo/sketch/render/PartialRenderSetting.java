package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

import java.util.Objects;

/**
 * Partial render setting for JSON loading (without render parameters)
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

    /**
     * Create a basic partial render setting
     */
    public static PartialRenderSetting basic(FullRenderState renderState, ResourceBinding resourceBinding) {
        return new PartialRenderSetting(renderState, resourceBinding, true);
    }

    /**
     * Create a compute shader partial setting
     */
    public static PartialRenderSetting computeShader(ResourceBinding resourceBinding) {
        return new PartialRenderSetting(null, resourceBinding, false);
    }
}