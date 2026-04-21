package rogo.sketch.core.extension.event;

import java.util.Objects;

/**
 * Typed host event contract used by module-owned subscriptions.
 */
public record HostEventContract<T>(
        String id,
        Class<T> eventType
) {
    public HostEventContract {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventType, "eventType");
    }

    public static <T> HostEventContract<T> of(String id, Class<T> eventType) {
        return new HostEventContract<>(id, eventType);
    }
}
