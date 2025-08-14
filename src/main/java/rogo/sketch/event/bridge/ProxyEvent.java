package rogo.sketch.event.bridge;

import net.minecraftforge.eventbus.api.GenericEvent;

public class ProxyEvent<T> extends GenericEvent<T> {
    private final T wrapped;

    public ProxyEvent(T wrapped) {
        this.wrapped = wrapped;
    }

    public T getWrapped() {
        return wrapped;
    }
}