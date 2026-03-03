package rogo.sketch.core.pipeline.flow.dirty;

import java.util.Objects;

/**
 * Dirty event for an instance.
 */
public record DirtyEvent<T>(T instance, DirtyReason reason) {
    public DirtyEvent {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(reason, "reason");
    }
}

