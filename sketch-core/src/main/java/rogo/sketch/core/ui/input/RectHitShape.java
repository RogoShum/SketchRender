package rogo.sketch.core.ui.input;

import rogo.sketch.core.ui.geometry.UiRect;

public record RectHitShape(UiRect bounds) implements HitShape {
    @Override
    public boolean contains(double x, double y) {
        return bounds.contains(x, y);
    }
}
