package rogo.sketch.backend.vulkan;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class VulkanDestructionQueue {
    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();

    void enqueue(Runnable cleanup) {
        if (cleanup != null) {
            pending.offer(cleanup);
        }
    }

    void drain() {
        Runnable cleanup;
        while ((cleanup = pending.poll()) != null) {
            cleanup.run();
        }
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }
}
