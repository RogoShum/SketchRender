package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

/**
 * Complete render setting including render parameters
 */
public record RenderSetting(
        FullRenderState renderState,
        ResourceBinding resourceBinding,
        RenderParameter renderParameter,
        boolean shouldSwitchRenderState
) implements ResourceObject {

    @Override
    public int getHandle() {
        return -1;
    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean isDisposed() {
        return false;
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