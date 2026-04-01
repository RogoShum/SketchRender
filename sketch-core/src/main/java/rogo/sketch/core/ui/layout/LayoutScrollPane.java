package rogo.sketch.core.ui.layout;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

public final class LayoutScrollPane extends LayoutNode {
    private final LayoutNode content;
    private final LayoutAxis axis;
    private double scrollOffset;
    private UiSize contentSize = UiSize.ZERO;

    public LayoutScrollPane(String id, LayoutNode content, LayoutAxis axis) {
        super(id);
        this.content = content;
        this.axis = axis;
        fillX(true);
        fillY(true);
    }

    public LayoutScrollPane scrollOffset(double scrollOffset) {
        this.scrollOffset = Math.max(0.0D, scrollOffset);
        return this;
    }

    public double scrollOffset() {
        return scrollOffset;
    }

    public UiSize contentSize() {
        return contentSize;
    }

    public LayoutNode content() {
        return content;
    }

    public UiRect viewportBounds() {
        return bounds();
    }

    @Override
    protected UiSize measureSelf(UiConstraints constraints) {
        contentSize = content.measure(measureConstraints(constraints));
        int width = preferredWidth() > 0 ? preferredWidth() : Math.min(constraints.maxWidth(), contentSize.width());
        int height = preferredHeight() > 0 ? preferredHeight() : Math.min(constraints.maxHeight(), contentSize.height());
        return new UiSize(width, height);
    }

    @Override
    protected void onLayout(UiRect bounds, @Nullable UiRect inheritedClipRect) {
        UiRect clipRect = intersect(inheritedClipRect, bounds);
        UiSize measured = content.measure(layoutMeasureConstraints(bounds));
        contentSize = measured;
        int x = bounds.x();
        int y = bounds.y();
        int width = Math.max(bounds.width(), measured.width());
        int height = Math.max(bounds.height(), measured.height());
        if (axis == LayoutAxis.VERTICAL) {
            y -= (int) Math.round(scrollOffset);
            width = bounds.width();
        } else {
            x -= (int) Math.round(scrollOffset);
            height = bounds.height();
        }
        content.layout(new UiRect(x, y, width, height), clipRect);
    }

    @Override
    public java.util.List<LayoutNode> children() {
        return java.util.List.of(content);
    }

    private UiConstraints measureConstraints(UiConstraints constraints) {
        return switch (axis) {
            case VERTICAL -> UiConstraints.of(constraints.maxWidth(), Integer.MAX_VALUE / 4);
            case HORIZONTAL -> UiConstraints.of(Integer.MAX_VALUE / 4, constraints.maxHeight());
        };
    }

    private UiConstraints layoutMeasureConstraints(UiRect bounds) {
        int minWidth = Math.max(bounds.width(), preferredWidth());
        int minHeight = Math.max(bounds.height(), preferredHeight());
        return switch (axis) {
            case VERTICAL -> UiConstraints.of(minWidth, Integer.MAX_VALUE / 4);
            case HORIZONTAL -> UiConstraints.of(Integer.MAX_VALUE / 4, minHeight);
        };
    }
}
