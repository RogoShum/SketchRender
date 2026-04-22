package rogo.sketch.core.dashboard;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.frame.UiInteractionSurface;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.ui.frame.UiLayer;
import rogo.sketch.core.ui.frame.UiPaintPass;
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
        Map<String, Object> props,
        UiPaintPass paintPass,
        UiInteractionSurface surface
) implements UiPrimitive {
    public DashboardPrimitive(String id, UiNodeType type, UiLayer layer, int order, UiRect bounds,
                              @Nullable UiRect clipRect, Map<String, Object> props) {
        this(id, type, layer, order, bounds, clipRect, props, UiPaintPass.DECORATION, UiInteractionSurface.content());
    }

    public DashboardPrimitive {
        props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
        paintPass = paintPass != null ? paintPass : UiPaintPass.DECORATION;
        surface = surface != null ? surface : UiInteractionSurface.content();
    }
}
