package rogo.sketchrender.render.sketch.uniform;

import rogo.sketchrender.api.ShaderProvider;
import rogo.sketchrender.api.ShaderResource;
import rogo.sketchrender.event.UniformHookRegisterEvent;
import rogo.sketchrender.event.bridge.EventBusBridge;
import rogo.sketchrender.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class UniformHookRegistry {
    private static final UniformHookRegistry INSTANCE = new UniformHookRegistry();
    private final Map<Identifier, ValueGetter<?>> pendingHooks = new HashMap<>();

    private UniformHookRegistry() {
    }

    public static UniformHookRegistry getInstance() {
        return INSTANCE;
    }

    public void init() {
        EventBusBridge.post(new UniformHookRegisterEvent(pendingHooks::put));
    }

    @SuppressWarnings("unchecked")
    public <T> void initializeHooks(ShaderProvider holder, Map<String, ShaderResource<?>> uniformMap) {
        for (Map.Entry<String, ShaderResource<?>> entry : uniformMap.entrySet()) {
            String uniformName = entry.getKey();
            ShaderResource<T> uniform = (ShaderResource<T>) entry.getValue();

            Identifier identifier = Identifier.of(uniformName);
            if (pendingHooks.containsKey(identifier)) {
                UniformHook<?> uniformHook = new UniformHook<>(identifier, uniform, (ValueGetter<T>) pendingHooks.get(identifier));
                holder.getUniformHookGroup().addUniform(uniformName, uniformHook);
            }
        }
    }
}