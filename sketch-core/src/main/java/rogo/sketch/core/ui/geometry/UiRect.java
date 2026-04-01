package rogo.sketch.core.ui.geometry;

public record UiRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public UiRect inset(int left, int top, int right, int bottom) {
        int nx = x + left;
        int ny = y + top;
        int nw = Math.max(0, width - left - right);
        int nh = Math.max(0, height - top - bottom);
        return new UiRect(nx, ny, nw, nh);
    }

    public static UiRect intersect(UiRect a, UiRect b) {
        int x = Math.max(a.x(), b.x());
        int y = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        return new UiRect(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }
}
