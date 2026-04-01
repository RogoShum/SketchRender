package rogo.sketch.core.ui.geometry;

public record UiTransform2D(double translateX, double translateY, double scaleX, double scaleY) {
    public UiTransform2D {
        scaleX = Math.abs(scaleX) < 0.000001D ? 1.0D : scaleX;
        scaleY = Math.abs(scaleY) < 0.000001D ? 1.0D : scaleY;
    }

    public static UiTransform2D identity() {
        return new UiTransform2D(0.0D, 0.0D, 1.0D, 1.0D);
    }

    public double screenX(double localX) {
        return translateX + localX * scaleX;
    }

    public double screenY(double localY) {
        return translateY + localY * scaleY;
    }

    public double localX(double screenX) {
        return (screenX - translateX) / scaleX;
    }

    public double localY(double screenY) {
        return (screenY - translateY) / scaleY;
    }

    public UiRect localToScreen(UiRect localRect) {
        int x = (int) Math.round(screenX(localRect.x()));
        int y = (int) Math.round(screenY(localRect.y()));
        int width = Math.max(0, (int) Math.round(localRect.width() * scaleX));
        int height = Math.max(0, (int) Math.round(localRect.height() * scaleY));
        return new UiRect(x, y, width, height);
    }

    public UiTransform2D translated(double dx, double dy) {
        return new UiTransform2D(translateX + dx, translateY + dy, scaleX, scaleY);
    }

    public UiTransform2D scaled(double zoom) {
        return new UiTransform2D(translateX, translateY, scaleX * zoom, scaleY * zoom);
    }
}
