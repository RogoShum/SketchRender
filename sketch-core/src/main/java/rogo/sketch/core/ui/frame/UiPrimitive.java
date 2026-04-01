package rogo.sketch.core.ui.frame;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

public interface UiPrimitive {
    UiLayer layer();

    int order();

    @Nullable UiRect clipRect();
}
