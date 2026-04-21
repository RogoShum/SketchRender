package rogo.sketch.core.extension.event;

import java.util.Objects;

/**
 * Owner-scoped host-event subscription handle.
 */
public final class ModuleHostEventSubscription<T> implements AutoCloseable {
    private final String ownerId;
    private final HostEventContract<T> contract;
    private final Runnable closeAction;
    private boolean closed;

    ModuleHostEventSubscription(String ownerId, HostEventContract<T> contract, Runnable closeAction) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.contract = Objects.requireNonNull(contract, "contract");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    public String ownerId() {
        return ownerId;
    }

    public HostEventContract<T> contract() {
        return contract;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeAction.run();
    }
}
