package rogo.sketchrender.render.component;

public class Texture {
    private final int handle;
    private final String identifier;
    private final int format;
    private final int filterMode;
    private int width, height;

    public Texture(int handle, String identifier, int format, int filterMode, int width, int height) {
        this.handle = handle;
        this.identifier = identifier;
        this.format = format;
        this.filterMode = filterMode;
        this.width = width;
        this.height = height;
    }
}