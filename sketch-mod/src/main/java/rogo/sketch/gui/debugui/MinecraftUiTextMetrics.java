package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import rogo.sketch.core.ui.text.UiText;
import rogo.sketch.core.ui.text.UiTextMetrics;

import java.util.ArrayList;
import java.util.List;

public final class MinecraftUiTextMetrics implements UiTextMetrics {
    private final Minecraft minecraft;

    public MinecraftUiTextMetrics() {
        this(Minecraft.getInstance());
    }

    public MinecraftUiTextMetrics(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public String resolve(UiText text) {
        if (text.translatable() && I18n.exists(text.value())) {
            return I18n.get(text.value());
        }
        return text.value();
    }

    @Override
    public int width(String resolvedText) {
        return minecraft.font.width(resolvedText);
    }

    @Override
    public int lineHeight() {
        return minecraft.font.lineHeight;
    }

    @Override
    public String clipWithEllipsis(UiText text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        String resolved = resolve(text);
        if (width(resolved) <= maxWidth) {
            return resolved;
        }
        String ellipsis = "...";
        int ellipsisWidth = width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return minecraft.font.plainSubstrByWidth(ellipsis, maxWidth);
        }
        String prefix = minecraft.font.plainSubstrByWidth(resolved, Math.max(0, maxWidth - ellipsisWidth));
        while (!prefix.isEmpty() && width(prefix + ellipsis) > maxWidth) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + ellipsis;
    }

    @Override
    public List<String> wrap(UiText text, int maxWidth) {
        if (maxWidth <= 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        String remaining = resolve(text);
        while (!remaining.isEmpty()) {
            String part = minecraft.font.plainSubstrByWidth(remaining, maxWidth);
            if (part.isEmpty()) {
                break;
            }
            lines.add(part);
            remaining = remaining.substring(part.length()).stripLeading();
        }
        return lines;
    }
}
