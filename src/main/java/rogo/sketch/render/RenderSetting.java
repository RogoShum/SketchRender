package rogo.sketch.render;

import rogo.sketch.render.component.RenderTarget;
import rogo.sketch.render.component.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;

public record RenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, RenderTarget renderTarget) {
}