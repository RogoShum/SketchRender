package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.RenderTarget;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState; /**
 * Partial render setting for JSON loading (without render parameters)
 */
public record PartialRenderSetting(
    FullRenderState renderState,
    ResourceBinding resourceBinding,
    RenderTarget renderTarget
) implements ResourceObject {
    
    @Override
    public int getHandle() {
        return -1;
    }

    @Override
    public void dispose() {
        // Partial settings don't own resources
    }
}