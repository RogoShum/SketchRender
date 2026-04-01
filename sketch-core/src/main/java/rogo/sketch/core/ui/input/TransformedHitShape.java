package rogo.sketch.core.ui.input;

import rogo.sketch.core.ui.geometry.UiTransform2D;

public record TransformedHitShape(HitShape localShape, UiTransform2D transform) implements HitShape {
    @Override
    public boolean contains(double x, double y) {
        return localShape.contains(transform.localX(x), transform.localY(y));
    }
}
