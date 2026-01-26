package rogo.sketch.event;

import rogo.sketch.core.event.bridge.IEventBusImplementation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FabricEventBusImplementation implements IEventBusImplementation {
    private final Map<Class<?>, List<EventListener<?>>> listeners = new HashMap<>();

    public FabricEventBusImplementation() {
    }

    @Override
    public <T> void post(T event) {
        List<EventListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (EventListener<?> listener : eventListeners) {
                @SuppressWarnings("unchecked")
                EventListener<T> castedListener = (EventListener<T>) listener;
                try {
                    castedListener.onEvent(event);
                } catch (Exception e) {
                    System.err.println("Error handling event in Fabric: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    public int getListenerCount() {
        return listeners.values().stream().mapToInt(List::size).sum();
    }

    public void clear() {
        listeners.clear();
    }
}