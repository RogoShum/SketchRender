package rogo.sketch.event.bridge;

import net.minecraftforge.eventbus.api.Event;

public class ProxyEvent<T> extends Event {
    private final T wrapped;

    public ProxyEvent(T wrapped) {
        this.wrapped = wrapped;
    }

    public T getWrapped() {
        return wrapped;
    }
}