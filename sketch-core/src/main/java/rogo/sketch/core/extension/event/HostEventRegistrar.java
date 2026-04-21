package rogo.sketch.core.extension.event;

import java.util.function.Consumer;

/**
 * Controlled host-event registration surface exposed to module runtimes.
 */
public interface HostEventRegistrar {
    <T> ModuleHostEventSubscription<T> subscribe(
            String ownerId,
            HostEventContract<T> contract,
            Consumer<T> listener);

    void clearOwner(String ownerId);
}
