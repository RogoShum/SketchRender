package rogo.sketch.core.pipeline.kernel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kernel-owned publication bus for cross-graph and cross-lane data handoff.
 * <p>
 * Each key maps to a last-write-wins slot that supports:
 * <ul>
 *   <li>publishing the latest completed payload for an epoch</li>
 *   <li>peeking the latest payload without consuming it</li>
 *   <li>consuming the latest payload exactly once when desired</li>
 * </ul>
 */
public final class KernelResourceBus {
    private final ConcurrentHashMap<KernelResourceKey<?>, ResourceSlot<?>> slots = new ConcurrentHashMap<>();

    public <T> PublishedKernelResource<T> publish(KernelResourceKey<T> key, long epoch, T payload) {
        return slot(key).publish(key, epoch, payload);
    }

    public <T> PublishedKernelResource<T> peek(KernelResourceKey<T> key) {
        return slot(key).peek();
    }

    public <T> PublishedKernelResource<T> consume(KernelResourceKey<T> key) {
        return slot(key).consume();
    }

    public <T> boolean hasResource(KernelResourceKey<T> key) {
        return peek(key) != null;
    }

    @SuppressWarnings("unchecked")
    private <T> ResourceSlot<T> slot(KernelResourceKey<T> key) {
        return (ResourceSlot<T>) slots.computeIfAbsent(key, ignored -> new ResourceSlot<>());
    }

    private static final class ResourceSlot<T> {
        private final AtomicReference<PublishedKernelResource<T>> latest = new AtomicReference<>();
        private final AtomicLong sequence = new AtomicLong();

        PublishedKernelResource<T> publish(KernelResourceKey<T> key, long epoch, T payload) {
            PublishedKernelResource<T> resource =
                    new PublishedKernelResource<>(key, payload, epoch, sequence.incrementAndGet());
            latest.set(resource);
            return resource;
        }

        PublishedKernelResource<T> peek() {
            return latest.get();
        }

        PublishedKernelResource<T> consume() {
            return latest.getAndSet(null);
        }
    }
}
