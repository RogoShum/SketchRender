package rogo.sketch.core.ui.frame;

import rogo.sketch.core.ui.input.HitRegion;

import java.util.List;

public record UiFrame(
        List<UiPrimitive> primitives,
        List<HitRegion> hitRegions
) {
    public UiFrame {
        primitives = List.copyOf(primitives);
        hitRegions = List.copyOf(hitRegions);
    }
}
