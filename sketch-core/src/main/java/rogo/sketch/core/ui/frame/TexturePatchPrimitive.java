package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.texture.UiTexturePatch;

public record TexturePatchPrimitive(
        UiLayer layer,
        int order,
        UiTexturePatch patch,
        UiRect rect,
        @Nullable UiRect clipRect,
        UiPaintPass paintPass,
        UiInteractionSurface surface
) implements UiPrimitive {
    public TexturePatchPrimitive(UiLayer layer, int order, UiTexturePatch patch, UiRect rect,
                                 @Nullable UiRect clipRect) {
        this(layer, order, patch, rect, clipRect, UiPaintPass.TEXTURE, UiInteractionSurface.content());
    }

    public TexturePatchPrimitive {
        if (patch == null) {
            throw new IllegalArgumentException("patch must not be null");
        }
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
        paintPass = paintPass != null ? paintPass : UiPaintPass.TEXTURE;
        surface = surface != null ? surface : UiInteractionSurface.content();
    }
}
