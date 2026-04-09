package rogo.sketch.core.driver.state;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.Map;

public class RenderStateBuilder {
    private final Map<KeyId, RenderStateComponent> components = new HashMap<>();

    public <T extends RenderStateComponent> RenderStateBuilder set(T component) {
        components.put(component.getIdentifier(), component);
        return this;
    }

    public RenderStatePatch build() {
        for (KeyId type : components.keySet()) {
            if (!DefaultRenderStates.isRegistered(type)) {
                throw new IllegalStateException("RenderStateComponent type not registered: " + type.getName());
            }
        }

        return DefaultRenderStates.createPatch(components);
    }
}

