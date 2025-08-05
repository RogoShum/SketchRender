package rogo.sketchrender.render;

import rogo.sketchrender.render.component.RenderTarget;
import rogo.sketchrender.render.component.TextureBinding;
import rogo.sketchrender.render.state.FullRenderState;

public record RenderSetting(FullRenderState renderState, TextureBinding textureBinding, RenderTarget renderTarget) {
}