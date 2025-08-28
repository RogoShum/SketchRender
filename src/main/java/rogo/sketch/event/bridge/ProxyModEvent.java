package rogo.sketch.event.bridge;

import net.minecraftforge.eventbus.api.GenericEvent;
import net.minecraftforge.fml.event.IModBusEvent;

public class ProxyModEvent<T extends RegistryEvent> extends GenericEvent<T> implements IModBusEvent {
    private final T wrapped;

    public ProxyModEvent(T wrapped) {
        super((Class<T>) wrapped.getClass());
        this.wrapped = wrapped;
    }

    public T getWrapped() {
        return wrapped;
    }
}