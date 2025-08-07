package rogo.sketchrender.render.sketch;

import rogo.sketchrender.render.sketch.component.RenderTarget;
import rogo.sketchrender.render.sketch.component.ResourceBinding;
import rogo.sketchrender.render.sketch.state.FullRenderState;

public record RenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, RenderTarget renderTarget) {
}