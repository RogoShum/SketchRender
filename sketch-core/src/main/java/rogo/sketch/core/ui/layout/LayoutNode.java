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
    private int maxWidth = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    private float growX;
    private float growY;
    private boolean fillX = true;
    private boolean fillY = true;
    private @Nullable Object payload;
    private UiInsets margin = UiInsets.NONE;
    private UiInsets border = UiInsets.NONE;
    private UiAxisAlignment alignX = UiAxisAlignment.CENTER;
    private UiAxisAlignment alignY = UiAxisAlignment.CENTER;
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

    public LayoutNode maxSize(int width, int height) {
        this.maxWidth = width > 0 ? width : Integer.MAX_VALUE;
        this.maxHeight = height > 0 ? height : Integer.MAX_VALUE;
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

    public LayoutNode margin(UiInsets margin) {
        this.margin = margin != null ? margin : UiInsets.NONE;
        return this;
    }

    public LayoutNode border(UiInsets border) {
        this.border = border != null ? border : UiInsets.NONE;
        return this;
    }

    public LayoutNode alignX(UiAxisAlignment alignX) {
        this.alignX = alignX != null ? alignX : UiAxisAlignment.CENTER;
        return this;
    }

    public LayoutNode alignY(UiAxisAlignment alignY) {
        this.alignY = alignY != null ? alignY : UiAxisAlignment.CENTER;
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

    public int maxWidth() {
        return maxWidth;
    }

    public int maxHeight() {
        return maxHeight;
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

    public UiInsets margin() {
        return margin;
    }

    public UiInsets border() {
        return border;
    }

    public UiAxisAlignment alignX() {
        return alignX;
    }

    public UiAxisAlignment alignY() {
        return alignY;
    }

    public UiRect bounds() {
        return bounds;
    }

    public @Nullable UiRect clipRect() {
        return clipRect;
    }

    public final UiSize measure(UiConstraints constraints) {
        int outerHorizontal = margin.horizontal() + border.horizontal();
        int outerVertical = margin.vertical() + border.vertical();
        UiSize measured = measureSelf(UiConstraints.of(
                Math.max(0, constraints.maxWidth() - outerHorizontal),
                Math.max(0, constraints.maxHeight() - outerVertical)));
        return new UiSize(
                clampSize(measured.width() + outerHorizontal, minWidth, maxWidth, constraints.maxWidth()),
                clampSize(measured.height() + outerVertical, minHeight, maxHeight, constraints.maxHeight()));
    }

    protected UiSize measureSelf(UiConstraints constraints) {
        int width = preferredWidth > 0 ? preferredWidth : minWidth;
        int height = preferredHeight > 0 ? preferredHeight : minHeight;
        return new UiSize(Math.min(width, constraints.maxWidth()), Math.min(height, constraints.maxHeight()));
    }

    public final void layout(UiRect bounds, @Nullable UiRect inheritedClipRect) {
        UiRect marginBounds = inset(bounds, margin);
        this.bounds = inset(marginBounds, border);
        this.clipRect = intersect(inheritedClipRect, this.bounds);
        onLayout(this.bounds, this.clipRect);
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

    protected int alignOffset(UiAxisAlignment alignment, int available, int used) {
        return switch (alignment) {
            case START -> 0;
            case END -> Math.max(0, available - used);
            case CENTER -> Math.max(0, (available - used) / 2);
        };
    }

    private int clampSize(int value, int min, int max, int constraint) {
        int resolvedMax = max > 0 ? max : Integer.MAX_VALUE;
        int clamped = Math.max(min, Math.min(value, resolvedMax));
        return Math.min(clamped, Math.max(0, constraint));
    }
}


