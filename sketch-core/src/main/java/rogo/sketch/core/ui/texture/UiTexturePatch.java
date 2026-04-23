package rogo.sketch.core.ui.texture;

import rogo.sketch.core.ui.layout.UiInsets;

public record UiTexturePatch(
        UiTextureRegion region,
        int tintArgb,
        UiInsets sliceInsets,
        UiTextureScaleMode scaleMode
) {
    public UiTexturePatch {
        if (region == null) {
            throw new IllegalArgumentException("region must not be null");
        }
        sliceInsets = sliceInsets != null ? sliceInsets : UiInsets.NONE;
        scaleMode = scaleMode != null ? scaleMode : UiTextureScaleMode.STRETCH;
    }

    public static UiTexturePatch stretch(UiTextureRegion region, int tintArgb) {
        return new UiTexturePatch(region, tintArgb, UiInsets.NONE, UiTextureScaleMode.STRETCH);
    }

    public static UiTexturePatch nineSlice(UiTextureRegion region, int tintArgb, UiInsets sliceInsets) {
        return new UiTexturePatch(region, tintArgb, sliceInsets, UiTextureScaleMode.NINE_SLICE);
    }

    public boolean isRenderable() {
        return region.texture().isRenderable();
    }
}
