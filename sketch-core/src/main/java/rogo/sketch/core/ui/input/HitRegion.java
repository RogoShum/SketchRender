package rogo.sketch.core.ui.input;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.frame.UiLayer;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record HitRegion(
        String id,
        UiLayer layer,
        int order,
        HitShape shape,
        @Nullable UiRect clipRect,
        CursorHint cursorHint,
        InputActionId actionId,
        Map<String, Object> props
) {
    public HitRegion {
        props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }

    public boolean contains(double x, double y) {
        return shape.contains(x, y) && (clipRect == null || clipRect.contains(x, y));
    }
}
