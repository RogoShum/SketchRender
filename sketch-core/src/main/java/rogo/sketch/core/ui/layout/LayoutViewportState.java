package rogo.sketch.core.ui.layout;

import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.geometry.UiTransform2D;

public record LayoutViewportState(double offsetX, double offsetY, double zoom) {
    public LayoutViewportState {
        zoom = Math.max(0.01D, zoom);
    }

    public static LayoutViewportState identity() {
        return new LayoutViewportState(0.0D, 0.0D, 1.0D);
    }

    public LayoutViewportState translated(double deltaX, double deltaY) {
        return new LayoutViewportState(offsetX + deltaX, offsetY + deltaY, zoom);
    }

    public LayoutViewportState zoomed(double newZoom) {
        return new LayoutViewportState(offsetX, offsetY, newZoom);
    }

    public UiTransform2D transform(UiRect viewportBounds) {
        return new UiTransform2D(viewportBounds.x() + offsetX, viewportBounds.y() + offsetY, zoom, zoom);
    }
}
