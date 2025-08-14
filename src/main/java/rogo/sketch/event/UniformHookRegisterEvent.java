package rogo.sketch.event;

import rogo.sketch.event.bridge.RegistryEvent;
import rogo.sketch.render.shader.uniform.ValueGetter;
import rogo.sketch.util.Identifier;

import java.util.function.BiConsumer;

public class UniformHookRegisterEvent implements RegistryEvent {
    private final BiConsumer<Identifier, ValueGetter<?>> register;

    public UniformHookRegisterEvent(BiConsumer<Identifier, ValueGetter<?>> register) {
        this.register = register;
    }

    public <T> void register(Identifier id, ValueGetter<T> operator) {
        register.accept(id, operator);
    }
}