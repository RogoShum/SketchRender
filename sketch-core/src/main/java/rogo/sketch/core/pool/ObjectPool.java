package rogo.sketch.core.pool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Generic object pool implementation for efficient object reuse
 *
 * @param <T> Type of objects to pool
 */
public class ObjectPool<T> {
    private final ConcurrentLinkedQueue<T> pool = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;
    private final int maxSize;
    private volatile int currentSize = 0;

    public ObjectPool(Supplier<T> factory) {
        this(factory, 128); // Default max size
    }

    public ObjectPool(Supplier<T> factory, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
    }

    /**
     * Borrow an object from the pool, creating a new one if necessary
     */
    public T borrow() {
        T object = pool.poll();
        if (object == null) {
            object = factory.get();
        } else {
            currentSize--;
        }
        return object;
    }

    /**
     * Return an object to the pool for reuse
     */
    public void returnObject(T object) {
        if (object != null && currentSize < maxSize) {
            if (object instanceof Poolable poolable) {
                poolable.reset();
            }
            pool.offer(object);
            currentSize++;
        }
    }

    /**
     * Clear all pooled objects
     */
    public void clear() {
        pool.clear();
        currentSize = 0;
    }

    /**
     * Get current pool size
     */
    public int size() {
        return currentSize;
    }

    /**
     * Get pool statistics
     */
    public PoolStats getStats() {
        return new PoolStats(currentSize, maxSize, pool.size());
    }

    public record PoolStats(int currentSize, int maxSize, int availableObjects) {
        @Override
        public String toString() {
            return String.format("Pool[size=%d/%d, available=%d]",
                    currentSize, maxSize, availableObjects);
        }
    }
}