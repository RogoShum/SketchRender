package rogo.sketch.core.ui.frame;

import rogo.sketch.core.ui.input.HitRegion;
import rogo.sketch.core.ui.geometry.UiScaleContext;

import java.util.List;

public record UiFrame(
        UiScaleContext scaleContext,
        List<UiPrimitive> primitives,
        List<HitRegion> hitRegions
) {
    public UiFrame {
        scaleContext = scaleContext != null ? scaleContext : UiScaleContext.of(1.0f, 1, 1);
        primitives = List.copyOf(primitives);
        hitRegions = List.copyOf(hitRegions);
    }
}
