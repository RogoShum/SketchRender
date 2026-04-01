package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiPoint;
import rogo.sketch.core.ui.geometry.UiRect;

public record LinePrimitive(
        UiLayer layer,
        int order,
        UiPoint from,
        UiPoint to,
        int color,
        int thickness,
        @Nullable UiRect clipRect
) implements UiPrimitive {
}
