package rogo.sketch.core.ui.layout;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.ArrayList;
import java.util.List;

public final class LayoutColumn extends LayoutContainer {
    public LayoutColumn(String id) {
        super(id);
    }

    @Override
    protected UiSize measureSelf(UiConstraints constraints) {
        UiRect dummy = new UiRect(0, 0, constraints.maxWidth(), constraints.maxHeight());
        UiRect inner = inset(dummy, padding());
        int contentWidth = 0;
        int contentHeight = padding().vertical();
        boolean first = true;
        for (LayoutNode child : children()) {
            UiSize childSize = child.measure(UiConstraints.of(inner.width(), inner.height()));
            contentWidth = Math.max(contentWidth, childSize.width());
            if (!first) {
                contentHeight += gap();
            }
            contentHeight += childSize.height();
            first = false;
        }
        int width = contentWidth + padding().horizontal();
        int height = contentHeight;
        if (preferredWidth() > 0) {
            width = Math.max(width, preferredWidth());
        }
        if (preferredHeight() > 0) {
            height = Math.max(height, preferredHeight());
        }
        return new UiSize(Math.min(width, constraints.maxWidth()), Math.min(height, constraints.maxHeight()));
    }

    @Override
    protected void onLayout(UiRect bounds, @Nullable UiRect inheritedClipRect) {
        UiRect inner = inset(bounds, padding());
        List<UiSize> measured = new ArrayList<>();
        int baseHeight = 0;
        float growTotal = 0.0f;
        for (LayoutNode child : children()) {
            UiSize size = child.measure(UiConstraints.of(inner.width(), Math.max(0, inner.height())));
            measured.add(size);
            baseHeight += size.height();
            growTotal += child.growY();
        }
        if (!children().isEmpty()) {
            baseHeight += gap() * (children().size() - 1);
        }
        int extra = Math.max(0, inner.height() - baseHeight);
        int y = inner.y();
        for (int index = 0; index < children().size(); index++) {
            LayoutNode child = children().get(index);
            UiSize measuredSize = measured.get(index);
            int height = measuredSize.height();
            if (extra > 0 && growTotal > 0.0f && child.growY() > 0.0f) {
                height += Math.round(extra * (child.growY() / growTotal));
            }
            int width = child.fillX() ? inner.width() : Math.min(inner.width(), Math.max(child.minWidth(), measuredSize.width()));
            int x = inner.x() + (child.fillX() ? 0 : alignOffset(child.alignX(), inner.width(), width));
            child.layout(new UiRect(x, y, width, height), inheritedClipRect);
            y += height + gap();
        }
    }
}
