package rogo.sketch.core.pipeline.flow.dirty;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Concurrent queue for dirty events.
 */
public class DirtyQueue<T> {
    private final Queue<DirtyEvent<T>> queue = new ConcurrentLinkedQueue<>();

    public void offer(T instance, DirtyReason reason) {
        queue.offer(new DirtyEvent<>(instance, reason));
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public List<DirtyEvent<T>> drain() {
        List<DirtyEvent<T>> events = new ArrayList<>();
        for (DirtyEvent<T> event = queue.poll(); event != null; event = queue.poll()) {
            events.add(event);
        }
        return events;
    }

    public void clear() {
        queue.clear();
    }
}

