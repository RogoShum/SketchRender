package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

import java.util.Objects;

public class RenderSetting implements ResourceObject {
    private final FullRenderState renderState;
    private final ResourceBinding resourceBinding;
    private final RenderParameter renderParameter;
    private final boolean shouldSwitchRenderState;
    private boolean disposed = false;

    public RenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter, boolean shouldSwitchRenderState) {
        this.renderState = renderState;
        this.resourceBinding = resourceBinding;
        this.renderParameter = renderParameter;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
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

    public static RenderSetting fromPartial(PartialRenderSetting partial, RenderParameter renderParameter) {
        return new RenderSetting(
                partial.renderState(),
                partial.resourceBinding(),
                renderParameter,
                partial.shouldSwitchRenderState()
        );
    }

    public static RenderSetting basic(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter) {
        return new RenderSetting(
                renderState,
                resourceBinding,
                renderParameter,
                true
        );
    }

    public static RenderSetting computeShader(PartialRenderSetting partial) {
        return new RenderSetting(
                partial.renderState(),
                partial.resourceBinding(),
                null,
                partial.shouldSwitchRenderState()
        );
    }
}