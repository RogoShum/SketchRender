package rogo.sketch.event.bridge;

import net.minecraftforge.eventbus.api.Event;

public class ProxyEvent extends Event {
    private final Object wrapped;

    public ProxyEvent(Object wrapped) {
        this.wrapped = wrapped;
    }

    public Object getWrapped() {
        return wrapped;
    }
}