package rogo.sketch.core.pipeline.module.runtime;

import java.util.Objects;

/**
 * Module-owned ECS subscription declaration.
 */
public record GraphicsEntitySubscription(
        String subscriptionId,
        GraphicsComponentFilter filter
) {
    public GraphicsEntitySubscription {
        subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        filter = Objects.requireNonNull(filter, "filter");
    }
}
