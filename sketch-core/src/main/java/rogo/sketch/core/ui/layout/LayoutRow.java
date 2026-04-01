package rogo.sketch.core.ui.layout;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.ArrayList;
import java.util.List;

public final class LayoutRow extends LayoutContainer {
    public LayoutRow(String id) {
        super(id);
    }

    @Override
    protected UiSize measureSelf(UiConstraints constraints) {
        UiRect dummy = new UiRect(0, 0, constraints.maxWidth(), constraints.maxHeight());
        UiRect inner = inset(dummy, padding());
        int contentWidth = padding().horizontal();
        int contentHeight = 0;
        boolean first = true;
        for (LayoutNode child : children()) {
            UiSize childSize = child.measure(UiConstraints.of(inner.width(), inner.height()));
            if (!first) {
                contentWidth += gap();
            }
            contentWidth += childSize.width();
            contentHeight = Math.max(contentHeight, childSize.height());
            first = false;
        }
        int width = contentWidth;
        int height = contentHeight + padding().vertical();
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
        int baseWidth = 0;
        float growTotal = 0.0f;
        for (LayoutNode child : children()) {
            UiSize size = child.measure(UiConstraints.of(Math.max(0, inner.width()), inner.height()));
            measured.add(size);
            baseWidth += size.width();
            growTotal += child.growX();
        }
        if (!children().isEmpty()) {
            baseWidth += gap() * (children().size() - 1);
        }
        int extra = Math.max(0, inner.width() - baseWidth);
        int x = inner.x();
        for (int index = 0; index < children().size(); index++) {
            LayoutNode child = children().get(index);
            UiSize measuredSize = measured.get(index);
            int width = measuredSize.width();
            if (extra > 0 && growTotal > 0.0f && child.growX() > 0.0f) {
                width += Math.round(extra * (child.growX() / growTotal));
            }
            int height = child.fillY() ? inner.height() : Math.min(inner.height(), Math.max(child.minHeight(), measuredSize.height()));
            int y = inner.y();
            if (!child.fillY()) {
                y = inner.y() + Math.max(0, (inner.height() - height) / 2);
            }
            child.layout(new UiRect(x, y, width, height), inheritedClipRect);
            x += width + gap();
        }
    }
}
