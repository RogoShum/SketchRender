package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

/**
 * Complete render setting including render parameters
 */
public record RenderSetting(
        FullRenderState renderState,       // Includes render target state now
        ResourceBinding resourceBinding,   // Resource bindings (textures, buffers, etc.)
        RenderParameter renderParameter,   // Vertex resource creation parameters
        boolean shouldSwitchRenderState   // Whether to apply render state (false for compute shaders)
) implements ResourceObject {

    @Override
    public int getHandle() {
        return -1;
    }

    @Override
    public void dispose() {

    }

    /**
     * Create RenderSetting from partial (JSON) data and runtime parameters
     */
    public static RenderSetting fromPartial(PartialRenderSetting partial, RenderParameter renderParameter) {
        return new RenderSetting(
                partial.renderState(),
                partial.resourceBinding(),
                renderParameter,
                partial.shouldSwitchRenderState()
        );
    }

    /**
     * Create a basic render setting with default draw command
     */
    public static RenderSetting basic(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter) {
        return new RenderSetting(
                renderState,
                resourceBinding,
                renderParameter,
                true // Switch render state by default
        );
    }

    /**
     * Create a compute shader setting (no render state switching)
     */
    public static RenderSetting computeShader(ResourceBinding resourceBinding) {
        return new RenderSetting(
                null, // No render state needed
                resourceBinding,
                null, // No draw command needed
                false // Don't switch render state
        );
    }
}