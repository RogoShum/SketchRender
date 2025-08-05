package rogo.sketchrender.event.bridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForgeEventBusImplementation implements IEventBusImplementation {
    private final Map<Class<?>, List<EventListener<?>>> listeners = new HashMap<>();

    public ForgeEventBusImplementation() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public <T> void post(T event) {
        MinecraftForge.EVENT_BUS.post(new ProxyEvent<>(event));
    }

    @Override
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    @SubscribeEvent
    public void onProxy(ProxyEvent<?> proxy) {
        Object wrapped = proxy.getWrapped();
        List<EventListener<?>> list = listeners.get(wrapped.getClass());
        if (list != null) {
            for (EventListener<?> l : list) {
                @SuppressWarnings("unchecked")
                EventListener<Object> casted = (EventListener<Object>) l;
                casted.onEvent(wrapped);
            }
        }
    }
}