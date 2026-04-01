package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

public record RectStrokePrimitive(
        UiLayer layer,
        int order,
        UiRect rect,
        int color,
        @Nullable UiRect clipRect
) implements UiPrimitive {
}
