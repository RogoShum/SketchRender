package rogo.sketch.api.graphics;

import rogo.sketch.render.RenderContext;
import rogo.sketch.render.RenderSetting;

/**
 * Provides render settings for graphics instances that need custom rendering state
 */
public interface RenderSettingProvider extends GraphicsInstance {
    
    /**
     * Get the render setting for this graphics instance
     * @param context The current render context
     * @return The render setting to use for this instance
     */
    <C extends RenderContext> RenderSetting getRenderSetting(C context);
}