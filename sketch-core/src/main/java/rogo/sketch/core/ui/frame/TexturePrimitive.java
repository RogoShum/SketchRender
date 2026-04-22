package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.texture.UiTextureRef;
import rogo.sketch.core.ui.texture.UiTextureUv;

public record TexturePrimitive(
        UiLayer layer,
        int order,
        UiTextureRef texture,
        UiRect rect,
        UiTextureUv uv,
        int tintArgb,
        @Nullable UiRect clipRect,
        UiPaintPass paintPass,
        UiInteractionSurface surface
) implements UiPrimitive {
    public TexturePrimitive(UiLayer layer, int order, UiTextureRef texture, UiRect rect, UiTextureUv uv, int tintArgb,
                            @Nullable UiRect clipRect) {
        this(layer, order, texture, rect, uv, tintArgb, clipRect, UiPaintPass.TEXTURE, UiInteractionSurface.content());
    }

    public TexturePrimitive {
        if (texture == null) {
            throw new IllegalArgumentException("texture must not be null");
        }
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
        uv = uv != null ? uv : UiTextureUv.FULL;
        paintPass = paintPass != null ? paintPass : UiPaintPass.TEXTURE;
        surface = surface != null ? surface : UiInteractionSurface.content();
    }
}
