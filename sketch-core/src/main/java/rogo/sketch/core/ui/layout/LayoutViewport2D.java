package rogo.sketch.core.ui.layout;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.geometry.UiTransform2D;

public final class LayoutViewport2D extends LayoutNode {
    private final LayoutNode content;
    private LayoutViewportState state = LayoutViewportState.identity();
    private UiSize contentSize = UiSize.ZERO;
    private UiRect viewportBounds = new UiRect(0, 0, 0, 0);
    private UiTransform2D transform = UiTransform2D.identity();

    public LayoutViewport2D(String id, LayoutNode content) {
        super(id);
        this.content = content;
        fillX(true);
        fillY(true);
    }

    public LayoutViewport2D state(LayoutViewportState state) {
        this.state = state != null ? state : LayoutViewportState.identity();
        return this;
    }

    public LayoutViewportState state() {
        return state;
    }

    public LayoutNode content() {
        return content;
    }

    public UiSize contentSize() {
        return contentSize;
    }

    public UiRect viewportBounds() {
        return viewportBounds;
    }

    public UiTransform2D transform() {
        return transform;
    }

    @Override
    protected UiSize measureSelf(UiConstraints constraints) {
        UiSize child = content.measure(constraints);
        contentSize = child;
        int width = preferredWidth() > 0 ? preferredWidth() : Math.min(constraints.maxWidth(), child.width());
        int height = preferredHeight() > 0 ? preferredHeight() : Math.min(constraints.maxHeight(), child.height());
        return new UiSize(width, height);
    }

    @Override
    protected void onLayout(UiRect bounds, @Nullable UiRect inheritedClipRect) {
        viewportBounds = bounds;
        transform = state.transform(bounds);
        UiRect clipRect = intersect(inheritedClipRect, bounds);
        UiSize child = content.measure(UiConstraints.of(Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4));
        contentSize = child;
        int width = Math.max(1, (int) Math.round(child.width() * state.zoom()));
        int height = Math.max(1, (int) Math.round(child.height() * state.zoom()));
        int x = (int) Math.round(transform.screenX(0.0D));
        int y = (int) Math.round(transform.screenY(0.0D));
        content.layout(new UiRect(x, y, width, height), clipRect);
    }

    @Override
    public java.util.List<LayoutNode> children() {
        return java.util.List.of(content);
    }
}
