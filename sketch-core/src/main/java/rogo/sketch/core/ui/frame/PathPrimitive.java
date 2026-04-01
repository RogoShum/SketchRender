package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiPoint;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.List;

public record PathPrimitive(
        UiLayer layer,
        int order,
        List<UiPoint> points,
        int color,
        int thickness,
        boolean closed,
        @Nullable UiRect clipRect
) implements UiPrimitive {
    public PathPrimitive {
        points = List.copyOf(points);
    }
}
