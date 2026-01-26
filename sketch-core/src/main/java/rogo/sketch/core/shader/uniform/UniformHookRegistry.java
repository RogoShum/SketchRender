package rogo.sketch.core.shader.uniform;

import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.core.event.UniformHookRegisterEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.Map;

public class UniformHookRegistry {
    private static final UniformHookRegistry INSTANCE = new UniformHookRegistry();
    private final Map<KeyId, ValueGetter<?>> pendingHooks = new HashMap<>();

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

            KeyId keyId = KeyId.of(uniformName);
            if (pendingHooks.containsKey(keyId)) {
                UniformHook<?> uniformHook = new UniformHook<>(keyId, uniform, (ValueGetter<T>) pendingHooks.get(keyId));
                provider.getUniformHookGroup().addUniform(uniformName, uniformHook);
            }
        }
    }
}