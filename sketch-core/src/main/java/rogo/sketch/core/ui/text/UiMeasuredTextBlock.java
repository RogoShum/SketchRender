package rogo.sketch.core.ui.text;

import java.util.List;

public record UiMeasuredTextBlock(
        List<String> lines,
        int lineHeight,
        int lineGap,
        int width,
        int height
) {
    public UiMeasuredTextBlock {
        lines = List.copyOf(lines);
        lineHeight = Math.max(1, lineHeight);
        lineGap = Math.max(0, lineGap);
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public int lineCount() {
        return lines.size();
    }

    public int lineY(int originY, int index) {
        return originY + index * (lineHeight + lineGap);
    }
}
