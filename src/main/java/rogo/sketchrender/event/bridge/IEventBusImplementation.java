package rogo.sketchrender.event.bridge;

public interface IEventBusImplementation {
    <T> void post(T event);
    <T> void subscribe(Class<T> eventType, EventListener<T> listener);

    interface EventListener<T> {
        void onEvent(T event);
    }
}
