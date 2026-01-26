package rogo.sketch.core.event;

import rogo.sketch.core.event.bridge.RegistryEvent;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;

import java.util.function.BiConsumer;

public class UniformHookRegisterEvent implements RegistryEvent {
    private final BiConsumer<KeyId, ValueGetter<?>> register;

    public UniformHookRegisterEvent(BiConsumer<KeyId, ValueGetter<?>> register) {
        this.register = register;
    }

    public <T> void register(KeyId id, ValueGetter<T> operator) {
        register.accept(id, operator);
    }
}