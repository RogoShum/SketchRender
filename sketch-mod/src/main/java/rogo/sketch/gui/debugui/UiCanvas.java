package rogo.sketch.gui.debugui;

import net.minecraft.network.chat.Component;
import rogo.sketch.core.ui.geometry.UiRect;

public interface UiCanvas extends UiTextMeasurer {
    void fillRect(UiRect rect, int color);

    void borderRect(UiRect rect, int color);

    void drawText(Component text, int x, int y, int color);

    void drawCenteredText(Component text, int centerX, int y, int color);

    void pushClip(UiRect rect);

    void popClip();

    void flush();
}

