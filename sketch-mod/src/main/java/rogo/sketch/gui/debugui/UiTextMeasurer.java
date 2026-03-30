package rogo.sketch.gui.debugui;

import net.minecraft.network.chat.Component;

public interface UiTextMeasurer {
    int width(Component text);

    int lineHeight();
}
