package rogo.sketch.render.state;


import rogo.sketch.api.RenderStateComponent;

import java.util.Map;
import java.util.Set;

public class FullRenderState {
    private final Map<Class<? extends RenderStateComponent>, RenderStateComponent> components;
    private final int hash;

    public FullRenderState(Map<Class<? extends RenderStateComponent>, RenderStateComponent> components) {
        this.components = Map.copyOf(components);
        this.hash = this.components.hashCode();
    }

    public <T extends RenderStateComponent> T get(Class<T> type) {
        return type.cast(components.get(type));
    }

    public Set<Class<? extends RenderStateComponent>> getComponentTypes() {
        return components.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FullRenderState)) return false;
        FullRenderState other = (FullRenderState) o;
        return components.equals(other.components);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}