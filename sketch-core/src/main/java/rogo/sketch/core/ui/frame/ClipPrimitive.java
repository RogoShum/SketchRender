package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

public record ClipPrimitive(
        UiLayer layer,
        int order,
        UiRect rect,
        @Nullable UiRect clipRect
) implements UiPrimitive {
}
