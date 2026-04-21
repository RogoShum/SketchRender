package rogo.sketch.core.extension.event;

import rogo.sketch.core.object.ObjectDespawnEvent;
import rogo.sketch.core.object.ObjectSpawnEvent;
import rogo.sketch.core.object.ObjectSyncEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Internal host-event bus used by object lifecycle ingress and module-owned
 * listeners.
 */
public final class ObjectLifecycleEventBus implements HostEventRegistrar {
    public static final HostEventContract<ObjectSpawnEvent> OBJECT_SPAWN =
            HostEventContract.of("object_spawn", ObjectSpawnEvent.class);
    public static final HostEventContract<ObjectSyncEvent> OBJECT_SYNC =
            HostEventContract.of("object_sync", ObjectSyncEvent.class);
    public static final HostEventContract<ObjectDespawnEvent> OBJECT_DESPAWN =
            HostEventContract.of("object_despawn", ObjectDespawnEvent.class);

    private final Map<String, List<ListenerBinding<?>>> listenersByContract = new LinkedHashMap<>();
    private final Map<String, List<ListenerBinding<?>>> bindingsByOwner = new LinkedHashMap<>();

    @Override
    public synchronized <T> ModuleHostEventSubscription<T> subscribe(
            String ownerId,
            HostEventContract<T> contract,
            Consumer<T> listener) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(listener, "listener");
        ListenerBinding<T> binding = new ListenerBinding<>(ownerId, contract, listener);
        listenersByContract
                .computeIfAbsent(contract.id(), ignored -> new ArrayList<>())
                .add(binding);
        ModuleHostEventSubscription<T> subscription = new ModuleHostEventSubscription<>(
                ownerId,
                contract,
                () -> removeBinding(binding));
        bindingsByOwner
                .computeIfAbsent(ownerId, ignored -> new ArrayList<>())
                .add(binding);
        return subscription;
    }

    @Override
    public synchronized void clearOwner(String ownerId) {
        if (ownerId == null) {
            return;
        }
        List<ListenerBinding<?>> bindings = bindingsByOwner.remove(ownerId);
        if (bindings == null) {
            return;
        }
        List<ListenerBinding<?>> snapshot = List.copyOf(bindings);
        for (ListenerBinding<?> binding : snapshot) {
            removeFromContract(binding);
        }
    }

    public synchronized <T> void post(HostEventContract<T> contract, T event) {
        if (contract == null || event == null) {
            return;
        }
        List<ListenerBinding<?>> listeners = listenersByContract.get(contract.id());
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        List<ListenerBinding<?>> snapshot = List.copyOf(listeners);
        for (ListenerBinding<?> listener : snapshot) {
            if (!contract.eventType().isInstance(event)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            ListenerBinding<T> typed = (ListenerBinding<T>) listener;
            typed.listener().accept(event);
        }
    }

    private synchronized <T> void removeBinding(ListenerBinding<T> binding) {
        removeFromContract(binding);
        List<ListenerBinding<?>> bindings = bindingsByOwner.get(binding.ownerId());
        if (bindings != null) {
            bindings.remove(binding);
            if (bindings.isEmpty()) {
                bindingsByOwner.remove(binding.ownerId());
            }
        }
    }

    private void removeFromContract(ListenerBinding<?> binding) {
        List<ListenerBinding<?>> listeners = listenersByContract.get(binding.contract().id());
        if (listeners != null) {
            listeners.remove(binding);
            if (listeners.isEmpty()) {
                listenersByContract.remove(binding.contract().id());
            }
        }
    }

    private record ListenerBinding<T>(
            String ownerId,
            HostEventContract<T> contract,
            Consumer<T> listener
    ) {
    }
}
