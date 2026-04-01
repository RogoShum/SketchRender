package rogo.sketch.core.ui.layout;

public record UiInsets(int left, int top, int right, int bottom) {
    public static final UiInsets NONE = new UiInsets(0, 0, 0, 0);

    public UiInsets {
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.max(0, right);
        bottom = Math.max(0, bottom);
    }

    public static UiInsets all(int value) {
        return new UiInsets(value, value, value, value);
    }

    public int horizontal() {
        return left + right;
    }

    public int vertical() {
        return top + bottom;
    }
}
