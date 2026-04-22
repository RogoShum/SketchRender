package rogo.sketch.core.debugger;

import rogo.sketch.core.ui.texture.UiTextureRef;
import rogo.sketch.core.ui.texture.UiTextureUv;

public record DashboardTexturePreview(
        String id,
        String titleKey,
        UiTextureRef texture,
        UiTextureUv uv,
        int width,
        int height,
        String detailText,
        int accentColor
) {
    public DashboardTexturePreview {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (titleKey == null || titleKey.isBlank()) {
            throw new IllegalArgumentException("titleKey must not be blank");
        }
        if (texture == null) {
            throw new IllegalArgumentException("texture must not be null");
        }
        uv = uv != null ? uv : UiTextureUv.FULL;
        width = Math.max(1, width);
        height = Math.max(1, height);
        detailText = detailText != null ? detailText : "";
    }
}
