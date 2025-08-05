package rogo.sketchrender.render;

import rogo.sketchrender.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class RenderContext {
    private final Map<Identifier, Object> contextMap = new HashMap<>();

    public void add(Identifier key, Object value) {
        contextMap.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Identifier key) {
        return (T) contextMap.get(key);
    }
}