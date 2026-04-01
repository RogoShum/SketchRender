package rogo.sketch.core.ui.layout;

public record UiSize(int width, int height) {
    public static final UiSize ZERO = new UiSize(0, 0);

    public UiSize {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }
}
