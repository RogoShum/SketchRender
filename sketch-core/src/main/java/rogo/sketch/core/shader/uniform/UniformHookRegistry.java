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
    private final ValueGetter<?> EMPTY_GETTER = ValueGetter.create(() -> null, Float.class);

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
        initializeHooksFromMap(provider.getHandle(), uniformMap, provider.getUniformHookGroup());
    }
    
    /**
     * Initialize hooks from a uniform map to a target hook group.
     * This variant doesn't require a ShaderProvider, useful for external initialization.
     * 
     * @param programId    The OpenGL program ID (unused but kept for signature consistency)
     * @param uniformMap   Map of uniform names to ShaderResource instances
     * @param targetGroup  The UniformHookGroup to add hooks to
     */
    @SuppressWarnings("unchecked")
    public <T> void initializeHooksFromMap(int programId, Map<String, ? extends ShaderResource<?>> uniformMap, UniformHookGroup targetGroup) {
        for (Map.Entry<String, ? extends ShaderResource<?>> entry : uniformMap.entrySet()) {
            String uniformName = entry.getKey();
            ShaderResource<T> uniform = (ShaderResource<T>) entry.getValue();

            KeyId keyId = KeyId.of(uniformName);
            if (pendingHooks.containsKey(keyId)) {
                UniformHook<?> uniformHook = new UniformHook<>(keyId, uniform, (ValueGetter<T>) pendingHooks.get(keyId));
                targetGroup.addUniform(uniformName, uniformHook);
            } else {
                UniformHook<?> uniformHook = new UniformHook<>(keyId, uniform, (ValueGetter<T>) EMPTY_GETTER);
                targetGroup.addUniform(uniformName, uniformHook);
            }
        }
    }
}