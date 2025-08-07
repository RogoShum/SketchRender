package rogo.sketchrender.event;

import rogo.sketchrender.render.sketch.uniform.ValueGetter;
import rogo.sketchrender.util.Identifier;

import java.util.function.BiConsumer;

public class UniformHookRegisterEvent {
    private final BiConsumer<Identifier, ValueGetter<?>> register;

    public UniformHookRegisterEvent(BiConsumer<Identifier, ValueGetter<?>> register) {
        this.register = register;
    }

    public <T> void register(Identifier id, ValueGetter<T> operator) {
        register.accept(id, operator);
    }
}