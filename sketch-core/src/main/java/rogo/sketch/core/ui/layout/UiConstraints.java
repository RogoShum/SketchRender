package rogo.sketch.core.ui.layout;

public record UiConstraints(int maxWidth, int maxHeight) {
    public UiConstraints {
        maxWidth = Math.max(0, maxWidth);
        maxHeight = Math.max(0, maxHeight);
    }

    public static UiConstraints of(int maxWidth, int maxHeight) {
        return new UiConstraints(maxWidth, maxHeight);
    }
}
