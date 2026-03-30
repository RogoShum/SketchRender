package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import rogo.sketch.core.debugger.ui.UiRect;

import java.util.ArrayDeque;
import java.util.Deque;

public class AdaptiveMinecraftUiCanvas implements UiCanvas {
    private final GuiGraphics guiGraphics;
    private final Minecraft minecraft;
    private final Deque<UiRect> clipStack = new ArrayDeque<>();

    public AdaptiveMinecraftUiCanvas(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    public void fillRect(UiRect rect, int color) {
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
    }

    @Override
    public void borderRect(UiRect rect, int color) {
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        guiGraphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        guiGraphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), color);
        guiGraphics.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), color);
    }

    @Override
    public void drawText(Component text, int x, int y, int color) {
        guiGraphics.drawString(minecraft.font, text, x, y, color, false);
    }

    @Override
    public void drawCenteredText(Component text, int centerX, int y, int color) {
        int drawX = centerX - minecraft.font.width(text) / 2;
        guiGraphics.drawString(minecraft.font, text, drawX, y, color, false);
    }

    @Override
    public int width(Component text) {
        return minecraft.font.width(text);
    }

    @Override
    public int lineHeight() {
        return minecraft.font.lineHeight;
    }

    @Override
    public void pushClip(UiRect rect) {
        UiRect clipped = clipStack.isEmpty() ? rect : intersect(clipStack.peek(), rect);
        clipStack.push(clipped);
        guiGraphics.enableScissor(clipped.x(), clipped.y(), clipped.right(), clipped.bottom());
    }

    @Override
    public void popClip() {
        if (clipStack.isEmpty()) {
            return;
        }
        clipStack.pop();
        if (clipStack.isEmpty()) {
            guiGraphics.disableScissor();
        } else {
            UiRect rect = clipStack.peek();
            guiGraphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        }
    }

    private UiRect intersect(UiRect a, UiRect b) {
        int x = Math.max(a.x(), b.x());
        int y = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        return new UiRect(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }
}
