package rogo.sketch.render.state;


import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.util.Identifier;

import java.util.Map;
import java.util.Set;

public class FullRenderState {
    private final Map<Identifier, ? extends RenderStateComponent> components;
    private final int hash;

    protected FullRenderState(Map<Identifier, RenderStateComponent> components) {
        this.components = Map.copyOf(components);
        this.hash = this.components.hashCode();
    }

    public RenderStateComponent get(Identifier type) {
        return components.get(type);
    }

    public Set<Identifier> getComponentTypes() {
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