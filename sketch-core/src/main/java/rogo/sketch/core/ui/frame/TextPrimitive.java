package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

public record TextPrimitive(
        UiLayer layer,
        int order,
        String text,
        int x,
        int y,
        int color,
        boolean centered,
        @Nullable UiRect clipRect
) implements UiPrimitive {
}
