package rogo.sketch.api.graphics;

import rogo.sketch.render.RenderContext;
import rogo.sketch.render.RenderSetting;

// Provider interfaces for extracting data from graphics instances
public interface RenderSettingProvider {
    <C extends RenderContext> RenderSetting getRenderSetting(C context);
}