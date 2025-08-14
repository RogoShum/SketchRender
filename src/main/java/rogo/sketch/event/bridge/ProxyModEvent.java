package rogo.sketch.event.bridge;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

public class ProxyModEvent extends Event implements IModBusEvent {
    private final RegistryEvent wrapped;

    public ProxyModEvent(RegistryEvent wrapped) {
        this.wrapped = wrapped;
    }

    public RegistryEvent getWrapped() {
        return wrapped;
    }
}