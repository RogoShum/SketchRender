package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

/**
 * Partial render setting for JSON loading (without render parameters)
 */
public record PartialRenderSetting(
    FullRenderState renderState,      // Includes render target state
    ResourceBinding resourceBinding,
    boolean shouldSwitchRenderState  // Whether to apply render state
) implements ResourceObject {
    
    @Override
    public int getHandle() {
        return -1;
    }

    @Override
    public void dispose() {
        // Partial settings don't own resources
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