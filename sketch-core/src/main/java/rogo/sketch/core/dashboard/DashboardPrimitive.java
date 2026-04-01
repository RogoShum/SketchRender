package rogo.sketch.core.dashboard;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.ui.frame.UiLayer;
import rogo.sketch.core.ui.frame.UiPrimitive;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DashboardPrimitive(
        String id,
        UiNodeType type,
        UiLayer layer,
        int order,
        UiRect bounds,
        @Nullable UiRect clipRect,
        Map<String, Object> props
) implements UiPrimitive {
    public DashboardPrimitive {
        props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }
}
