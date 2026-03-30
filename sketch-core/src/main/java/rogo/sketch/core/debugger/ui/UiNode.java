package rogo.sketch.core.debugger.ui;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UiNode {
    private final String id;
    private final UiNodeType type;
    private final UiPass pass;
    private final UiRect bounds;
    private final @Nullable UiRect clipRect;
    private final Map<String, Object> props;

    public UiNode(String id, UiNodeType type, UiPass pass, UiRect bounds, @Nullable UiRect clipRect, Map<String, Object> props) {
        this.id = id;
        this.type = type;
        this.pass = pass;
        this.bounds = bounds;
        this.clipRect = clipRect;
        this.props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }

    public String id() {
        return id;
    }

    public UiNodeType type() {
        return type;
    }

    public UiPass pass() {
        return pass;
    }

    public UiRect bounds() {
        return bounds;
    }

    public @Nullable UiRect clipRect() {
        return clipRect;
    }

    public Map<String, Object> props() {
        return props;
    }
}
