package rogo.sketch.core.ui.text;

import java.util.List;

public interface UiTextMetrics {
    String resolve(UiText text);

    int width(String resolvedText);

    default int width(UiText text) {
        return width(resolve(text));
    }

    int lineHeight();

    String clipWithEllipsis(UiText text, int maxWidth);

    List<String> wrap(UiText text, int maxWidth);
}
