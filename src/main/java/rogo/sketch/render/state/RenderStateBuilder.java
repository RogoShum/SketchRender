package rogo.sketch.render.state;

import rogo.sketch.api.RenderStateComponent;

import java.util.HashMap;
import java.util.Map;

public class RenderStateBuilder {
    private final Map<Class<? extends RenderStateComponent>, RenderStateComponent> components = new HashMap<>();

    public <T extends RenderStateComponent> RenderStateBuilder set(T component) {
        components.put(component.getType(), component);
        return this;
    }

    public FullRenderState build() {
        for (Class<? extends RenderStateComponent> type : components.keySet()) {
            if (!RenderStateRegistry.isRegistered(type)) {
                throw new IllegalStateException("RenderStateComponent type not registered: " + type.getName());
            }
        }

        Map<Class<? extends RenderStateComponent>, RenderStateComponent> all = new HashMap<>(RenderStateRegistry.getDefaults());
        all.putAll(components);
        return new FullRenderState(all);
    }
}