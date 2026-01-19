package rogo.sketch.event;

import rogo.sketch.event.bridge.RegistryEvent;
import rogo.sketch.render.shader.uniform.ValueGetter;
import rogo.sketch.util.KeyId;

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