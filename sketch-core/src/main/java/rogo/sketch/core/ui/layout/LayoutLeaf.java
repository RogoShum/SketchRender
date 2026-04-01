package rogo.sketch.core.ui.layout;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

public final class LayoutLeaf extends LayoutNode {
    public LayoutLeaf(String id) {
        super(id);
    }

    @Override
    protected void onLayout(UiRect bounds, @Nullable UiRect inheritedClipRect) {
    }
}
