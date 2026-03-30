package rogo.sketch.core.debugger.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UiScene {
    private final List<UiNode> nodes = new ArrayList<>();

    public void add(UiNode node) {
        nodes.add(node);
    }

    public List<UiNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }
}
