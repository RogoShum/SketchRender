package rogo.sketch.core.pipeline.module.runtime;

/**
 * Declarative collector for module ECS subscriptions.
 */
public interface ModuleSubscriptionRegistrar {
    void register(GraphicsEntitySubscription subscription);

    default void register(String subscriptionId, GraphicsComponentFilter filter) {
        register(new GraphicsEntitySubscription(subscriptionId, filter));
    }
}
