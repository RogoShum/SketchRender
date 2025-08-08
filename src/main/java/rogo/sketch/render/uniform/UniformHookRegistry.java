package rogo.sketch.render.uniform;

import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.event.UniformHookRegisterEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.util.Identifier;

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
    public <T> void initializeHooks(ShaderProvider provider, Map<String, ShaderResource<?>> uniformMap) {
        for (Map.Entry<String, ShaderResource<?>> entry : uniformMap.entrySet()) {
            String uniformName = entry.getKey();
            ShaderResource<T> uniform = (ShaderResource<T>) entry.getValue();

            Identifier identifier = Identifier.of(uniformName);
            if (pendingHooks.containsKey(identifier)) {
                UniformHook<?> uniformHook = new UniformHook<>(identifier, uniform, (ValueGetter<T>) pendingHooks.get(identifier));
                provider.getUniformHookGroup().addUniform(uniformName, uniformHook);
            }
        }
    }
}