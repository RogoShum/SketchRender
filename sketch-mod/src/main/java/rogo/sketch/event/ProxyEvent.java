package rogo.sketch.event;

import net.minecraftforge.eventbus.api.GenericEvent;

public class ProxyEvent<T> extends GenericEvent<T> {
    private final T wrapped;

    public ProxyEvent(T wrapped) {
        super((Class<T>) wrapped.getClass());
        this.wrapped = wrapped;
    }

    public T getWrapped() {
        return wrapped;
    }
}