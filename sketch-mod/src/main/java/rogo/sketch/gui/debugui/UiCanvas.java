package rogo.sketch.gui.debugui;

import net.minecraft.network.chat.Component;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.geometry.UiScaleContext;
import rogo.sketch.core.ui.texture.UiTextureRef;
import rogo.sketch.core.ui.texture.UiTextureUv;

public interface UiCanvas extends UiTextMeasurer {
    void beginFrame(UiScaleContext scaleContext);

    void endFrame();

    void fillRect(UiRect rect, int color);

    void borderRect(UiRect rect, int color);

    void drawText(Component text, int x, int y, int color);

    void drawCenteredText(Component text, int centerX, int y, int color);

    void drawTexture(UiTextureRef texture, UiRect dst, UiTextureUv uv, int tintArgb);

    void pushClip(UiRect rect);

    void popClip();

    void flush();
}

