package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.RenderTarget;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

/**
 * Complete render setting including render parameters
 */
public record RenderSetting(
    FullRenderState renderState, 
    ResourceBinding resourceBinding, 
    RenderTarget renderTarget, 
    RenderParameter renderParameter
) implements ResourceObject {
    
    @Override
    public int getHandle() {
        return -1;
    }

    @Override
    public void dispose() {
        // Render settings don't own resources, so no disposal needed
    }
    
    /**
     * Create RenderSetting from partial (JSON) data and runtime parameters
     */
    public static RenderSetting fromPartial(PartialRenderSetting partial, RenderParameter renderParameter) {
        return new RenderSetting(
            partial.renderState(),
            partial.resourceBinding(),
            partial.renderTarget(),
            renderParameter
        );
    }
}

