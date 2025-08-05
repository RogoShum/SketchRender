package rogo.sketchrender.event.bridge;

public class EventBusBridge {
    private static IEventBusImplementation implementation;

    public static void setImplementation(IEventBusImplementation impl) {
        implementation = impl;
    }

    public static <T> void post(T event) {
        implementation.post(event);
    }

    public static <T> void subscribe(Class<T> eventType, IEventBusImplementation.EventListener<T> listener) {
        implementation.subscribe(eventType, listener);
    }
}