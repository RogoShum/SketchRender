package rogo.sketch.core.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class LayoutContainer extends LayoutNode {
    private final List<LayoutNode> children = new ArrayList<>();
    private UiInsets padding = UiInsets.NONE;
    private int gap;

    protected LayoutContainer(String id) {
        super(id);
    }

    public LayoutContainer padding(UiInsets padding) {
        this.padding = padding != null ? padding : UiInsets.NONE;
        return this;
    }

    public LayoutContainer gap(int gap) {
        this.gap = Math.max(0, gap);
        return this;
    }

    public LayoutContainer child(LayoutNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    public UiInsets padding() {
        return padding;
    }

    public int gap() {
        return gap;
    }

    @Override
    public List<LayoutNode> children() {
        return Collections.unmodifiableList(children);
    }
}
