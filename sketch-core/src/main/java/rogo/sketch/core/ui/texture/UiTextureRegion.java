package rogo.sketch.core.ui.texture;

public record UiTextureRegion(
        UiTextureRef texture,
        UiTextureUv uv,
        int atlasWidth,
        int atlasHeight,
        int sourceWidth,
        int sourceHeight
) {
    public UiTextureRegion {
        if (texture == null) {
            throw new IllegalArgumentException("texture must not be null");
        }
        uv = uv != null ? uv : UiTextureUv.FULL;
        if (atlasWidth <= 0 || atlasHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("texture region dimensions must be positive");
        }
    }

    public static UiTextureRegion full(UiTextureRef texture, int sourceWidth, int sourceHeight) {
        return new UiTextureRegion(texture, UiTextureUv.FULL, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    public static UiTextureRegion fromPixels(UiTextureRef texture, int atlasWidth, int atlasHeight,
                                             int x, int y, int width, int height) {
        if (atlasWidth <= 0 || atlasHeight <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture region dimensions must be positive");
        }
        float u0 = x / (float) atlasWidth;
        float v0 = y / (float) atlasHeight;
        float u1 = (x + width) / (float) atlasWidth;
        float v1 = (y + height) / (float) atlasHeight;
        return new UiTextureRegion(texture, new UiTextureUv(u0, v0, u1, v1), atlasWidth, atlasHeight, width, height);
    }
}
