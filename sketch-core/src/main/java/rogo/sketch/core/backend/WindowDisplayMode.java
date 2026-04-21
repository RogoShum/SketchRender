package rogo.sketch.core.backend;

public record WindowDisplayMode(int width, int height, int refreshRate) {
    public WindowDisplayMode {
        width = Math.max(1, width);
        height = Math.max(1, height);
        refreshRate = Math.max(0, refreshRate);
    }

    public String label() {
        return width + "x" + height;
    }
}
