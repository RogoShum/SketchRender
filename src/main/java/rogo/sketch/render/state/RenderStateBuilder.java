package rogo.sketch.render.state;

import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class RenderStateBuilder {
    private final Map<Identifier, RenderStateComponent> components = new HashMap<>();

    public <T extends RenderStateComponent> RenderStateBuilder set(T component) {
        components.put(component.getIdentifier(), component);
        return this;
    }

    public FullRenderState build() {
        for (Identifier type : components.keySet()) {
            if (!RenderStateRegistry.isRegistered(type)) {
                throw new IllegalStateException("RenderStateComponent type not registered: " + type.getName());
            }
        }

        return RenderStateRegistry.createFullRenderState(components);
    }
}