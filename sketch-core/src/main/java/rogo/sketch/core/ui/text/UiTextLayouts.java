package rogo.sketch.core.ui.text;

import java.util.ArrayList;
import java.util.List;

public final class UiTextLayouts {
    private UiTextLayouts() {
    }

    public static UiMeasuredTextBlock measureWrapped(UiTextMetrics metrics, String text, int maxWidth, int lineGap) {
        if (metrics == null) {
            List<String> lines = List.of(text != null ? text : "");
            int width = lines.stream().mapToInt(String::length).max().orElse(0) * 6;
            int lineHeight = 9;
            int height = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * Math.max(0, lineGap);
            return new UiMeasuredTextBlock(lines, lineHeight, lineGap, width, height);
        }

        int safeWidth = Math.max(1, maxWidth);
        List<String> lines = new ArrayList<>();
        String safeText = text != null ? text : "";
        if (safeText.isBlank()) {
            lines.add("");
        } else {
            for (String physicalLine : safeText.split("\\R", -1)) {
                List<String> wrapped = metrics.wrap(UiText.literal(physicalLine), safeWidth);
                if (wrapped.isEmpty()) {
                    lines.add("");
                } else {
                    lines.addAll(wrapped);
                }
            }
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, metrics.width(line));
        }
        int lineHeight = metrics.lineHeight();
        int height = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * Math.max(0, lineGap);
        return new UiMeasuredTextBlock(lines, lineHeight, lineGap, width, height);
    }

    public static String clipWithEllipsis(UiTextMetrics metrics, String text, int maxWidth) {
        if (metrics == null) {
            if (text == null) {
                return "";
            }
            int maxChars = Math.max(0, maxWidth / 6);
            if (text.length() <= maxChars) {
                return text;
            }
            return maxChars <= 3 ? text.substring(0, Math.max(0, maxChars)) : text.substring(0, maxChars - 3) + "...";
        }
        return metrics.clipWithEllipsis(UiText.literal(text != null ? text : ""), maxWidth);
    }
}
