package rogo.sketch.platformtest;

import rogo.sketch.core.event.bridge.IEventBusImplementation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class PlatformTestEventBusImplementation implements IEventBusImplementation {
    private final Map<Class<?>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <T> void post(T event) {
        if (event == null) {
            return;
        }
        Class<?> eventType = event.getClass();
        listeners.forEach((registeredType, registeredListeners) -> {
            if (!registeredType.isAssignableFrom(eventType)) {
                return;
            }
            for (EventListener<?> registeredListener : registeredListeners) {
                @SuppressWarnings("unchecked")
                EventListener<T> typedListener = (EventListener<T>) registeredListener;
                typedListener.onEvent(event);
            }
        });
    }

    @Override
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        if (eventType == null || listener == null) {
            return;
        }
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(listener);
    }
}

