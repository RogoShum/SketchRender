package rogo.sketch.core.ui.texture;

public record UiTextureUv(float u0, float v0, float u1, float v1) {
    public static final UiTextureUv FULL = new UiTextureUv(0.0F, 0.0F, 1.0F, 1.0F);
    public static final UiTextureUv FLIP_Y = new UiTextureUv(0.0F, 1.0F, 1.0F, 0.0F);

    public UiTextureUv {
        if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)) {
            throw new IllegalArgumentException("UV values must be finite");
        }
    }
}
