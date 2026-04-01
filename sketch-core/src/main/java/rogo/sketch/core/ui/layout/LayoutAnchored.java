package rogo.sketch.core.ui.layout;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

public final class LayoutAnchored extends LayoutNode {
    public enum HorizontalAlign {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum VerticalAlign {
        TOP,
        CENTER,
        BOTTOM
    }

    private final LayoutNode child;
    private HorizontalAlign horizontalAlign = HorizontalAlign.LEFT;
    private VerticalAlign verticalAlign = VerticalAlign.TOP;
    private int offsetX;
    private int offsetY;

    public LayoutAnchored(String id, LayoutNode child) {
        super(id);
        this.child = child;
        fillX(true);
        fillY(true);
    }

    public LayoutAnchored horizontalAlign(HorizontalAlign horizontalAlign) {
        this.horizontalAlign = horizontalAlign != null ? horizontalAlign : HorizontalAlign.LEFT;
        return this;
    }

    public LayoutAnchored verticalAlign(VerticalAlign verticalAlign) {
        this.verticalAlign = verticalAlign != null ? verticalAlign : VerticalAlign.TOP;
        return this;
    }

    public LayoutAnchored offset(int offsetX, int offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        return this;
    }

    public LayoutNode child() {
        return child;
    }

    @Override
    protected UiSize measureSelf(UiConstraints constraints) {
        return child.measure(constraints);
    }

    @Override
    protected void onLayout(UiRect bounds, @Nullable UiRect inheritedClipRect) {
        UiSize childSize = child.measure(UiConstraints.of(bounds.width(), bounds.height()));
        int width = child.fillX() ? bounds.width() : Math.min(bounds.width(), Math.max(child.minWidth(), childSize.width()));
        int height = child.fillY() ? bounds.height() : Math.min(bounds.height(), Math.max(child.minHeight(), childSize.height()));
        int x = switch (horizontalAlign) {
            case LEFT -> bounds.x() + offsetX;
            case CENTER -> bounds.x() + (bounds.width() - width) / 2 + offsetX;
            case RIGHT -> bounds.right() - width - offsetX;
        };
        int y = switch (verticalAlign) {
            case TOP -> bounds.y() + offsetY;
            case CENTER -> bounds.y() + (bounds.height() - height) / 2 + offsetY;
            case BOTTOM -> bounds.bottom() - height - offsetY;
        };
        child.layout(new UiRect(x, y, width, height), inheritedClipRect);
    }

    @Override
    public java.util.List<LayoutNode> children() {
        return java.util.List.of(child);
    }
}
