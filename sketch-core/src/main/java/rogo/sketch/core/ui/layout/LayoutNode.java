package rogo.sketch.core.ui.layout;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.List;
import java.util.function.Consumer;

public abstract class LayoutNode {
    private final String id;
    private int preferredWidth;
    private int preferredHeight;
    private int minWidth;
    private int minHeight;
    private float growX;
    private float growY;
    private boolean fillX = true;
    private boolean fillY = true;
    private @Nullable Object payload;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private @Nullable UiRect clipRect;

    protected LayoutNode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public LayoutNode preferredSize(int width, int height) {
        this.preferredWidth = Math.max(0, width);
        this.preferredHeight = Math.max(0, height);
        return this;
    }

    public LayoutNode preferredWidth(int width) {
        this.preferredWidth = Math.max(0, width);
        return this;
    }

    public LayoutNode preferredHeight(int height) {
        this.preferredHeight = Math.max(0, height);
        return this;
    }

    public LayoutNode minSize(int width, int height) {
        this.minWidth = Math.max(0, width);
        this.minHeight = Math.max(0, height);
        return this;
    }

    public LayoutNode growX(float value) {
        this.growX = Math.max(0.0f, value);
        return this;
    }

    public LayoutNode growY(float value) {
        this.growY = Math.max(0.0f, value);
        return this;
    }

    public LayoutNode fillX(boolean value) {
        this.fillX = value;
        return this;
    }

    public LayoutNode fillY(boolean value) {
        this.fillY = value;
        return this;
    }

    public LayoutNode payload(@Nullable Object payload) {
        this.payload = payload;
        return this;
    }

    public int preferredWidth() {
        return preferredWidth;
    }

    public int preferredHeight() {
        return preferredHeight;
    }

    public int minWidth() {
        return minWidth;
    }

    public int minHeight() {
        return minHeight;
    }

    public float growX() {
        return growX;
    }

    public float growY() {
        return growY;
    }

    public boolean fillX() {
        return fillX;
    }

    public boolean fillY() {
        return fillY;
    }

    public @Nullable Object payload() {
        return payload;
    }

    public UiRect bounds() {
        return bounds;
    }

    public @Nullable UiRect clipRect() {
        return clipRect;
    }

    public final UiSize measure(UiConstraints constraints) {
        UiSize measured = measureSelf(constraints);
        return new UiSize(
                Math.max(minWidth, measured.width()),
                Math.max(minHeight, measured.height()));
    }

    protected UiSize measureSelf(UiConstraints constraints) {
        int width = preferredWidth > 0 ? preferredWidth : minWidth;
        int height = preferredHeight > 0 ? preferredHeight : minHeight;
        return new UiSize(Math.min(width, constraints.maxWidth()), Math.min(height, constraints.maxHeight()));
    }

    public final void layout(UiRect bounds, @Nullable UiRect inheritedClipRect) {
        this.bounds = bounds;
        this.clipRect = intersect(inheritedClipRect, bounds);
        onLayout(bounds, this.clipRect);
    }

    protected abstract void onLayout(UiRect bounds, @Nullable UiRect inheritedClipRect);

    public List<LayoutNode> children() {
        return List.of();
    }

    public void visit(Consumer<LayoutNode> visitor) {
        visitor.accept(this);
        for (LayoutNode child : children()) {
            child.visit(visitor);
        }
    }

    protected UiRect inset(UiRect rect, UiInsets insets) {
        int x = rect.x() + insets.left();
        int y = rect.y() + insets.top();
        int width = Math.max(0, rect.width() - insets.horizontal());
        int height = Math.max(0, rect.height() - insets.vertical());
        return new UiRect(x, y, width, height);
    }

    protected @Nullable UiRect intersect(@Nullable UiRect a, UiRect b) {
        if (a == null) {
            return b;
        }
        int x = Math.max(a.x(), b.x());
        int y = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        return new UiRect(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }
}


