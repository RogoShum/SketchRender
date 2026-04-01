package rogo.sketch.core.ui.layout;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;
public final class LayoutStack extends LayoutContainer {
    public LayoutStack(String id) {
        super(id);
    }
    @Override
    protected UiSize measureSelf(UiConstraints constraints) {
        UiRect dummy = new UiRect(0, 0, constraints.maxWidth(), constraints.maxHeight());
        UiRect inner = inset(dummy, padding());
        int contentWidth = 0;
        int contentHeight = 0;
        for (LayoutNode child : children()) {
            UiSize childSize = child.measure(UiConstraints.of(inner.width(), inner.height()));
            contentWidth = Math.max(contentWidth, childSize.width());
            contentHeight = Math.max(contentHeight, childSize.height());
        }
        int width = contentWidth + padding().horizontal();
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
        for (LayoutNode child : children()) {
            UiSize measured = child.measure(UiConstraints.of(inner.width(), inner.height()));
            int width = child.fillX() ? inner.width() : Math.min(inner.width(), Math.max(child.minWidth(), measured.width()));
            int height = child.fillY() ? inner.height() : Math.min(inner.height(), Math.max(child.minHeight(), measured.height()));
            child.layout(new UiRect(inner.x(), inner.y(), width, height), inheritedClipRect);
        }
    }
}
