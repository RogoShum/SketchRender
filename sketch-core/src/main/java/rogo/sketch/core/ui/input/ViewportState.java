package rogo.sketch.core.ui.input;

public record ViewportState(double offsetX, double offsetY, double zoom) {
    public static ViewportState identity() {
        return new ViewportState(0.0D, 0.0D, 1.0D);
    }
}
