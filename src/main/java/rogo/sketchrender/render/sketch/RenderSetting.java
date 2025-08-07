package rogo.sketchrender.render.sketch;

import rogo.sketchrender.render.sketch.component.RenderTarget;
import rogo.sketchrender.render.sketch.component.TextureBinding;
import rogo.sketchrender.render.sketch.state.FullRenderState;

public record RenderSetting(FullRenderState renderState, TextureBinding textureBinding, RenderTarget renderTarget) {
}