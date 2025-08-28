package rogo.sketch.render.pool;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manages object pools for different types of GraphicsInstance objects
 */
public class InstancePoolManager {
    private static final InstancePoolManager INSTANCE = new InstancePoolManager();

    private final Map<Class<?>, ObjectPool<?>> typePools = new ConcurrentHashMap<>();
    private final Map<Identifier, ObjectPool<GraphicsInstance>> namedPools = new ConcurrentHashMap<>();
    private boolean poolingEnabled = true;

    private InstancePoolManager() {
    }

    public static InstancePoolManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register a pool for a specific GraphicsInstance type
     */
    public <T extends GraphicsInstance> void registerTypePool(Class<T> type, Supplier<T> factory) {
        registerTypePool(type, factory, 128);
    }

    public <T extends GraphicsInstance> void registerTypePool(Class<T> type, Supplier<T> factory, int maxSize) {
        typePools.put(type, new ObjectPool<>(factory, maxSize));
    }

    /**
     * Register a named pool for specific instance categories
     */
    public void registerNamedPool(Identifier poolName, Supplier<GraphicsInstance> factory) {
        registerNamedPool(poolName, factory, 128);
    }

    public void registerNamedPool(Identifier poolName, Supplier<GraphicsInstance> factory, int maxSize) {
        namedPools.put(poolName, new ObjectPool<>(factory, maxSize));
    }

    /**
     * Borrow instance from type-based pool
     */
    @SuppressWarnings("unchecked")
    public <T extends GraphicsInstance> T borrowInstance(Class<T> type) {
        if (!poolingEnabled) {
            throw new IllegalStateException("Pooling is disabled, create instances manually");
        }

        ObjectPool<T> pool = (ObjectPool<T>) typePools.get(type);
        if (pool == null) {
            throw new IllegalArgumentException("No pool registered for type: " + type.getName());
        }

        return pool.borrow();
    }

    /**
     * Borrow instance from named pool
     */
    public GraphicsInstance borrowInstance(Identifier poolName) {
        if (!poolingEnabled) {
            throw new IllegalStateException("Pooling is disabled, create instances manually");
        }

        ObjectPool<GraphicsInstance> pool = namedPools.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("No pool registered for name: " + poolName);
        }

        return pool.borrow();
    }

    /**
     * Return instance to appropriate pool
     */
    public void returnInstance(GraphicsInstance instance) {
        if (!poolingEnabled) {
            return; // Just ignore if pooling is disabled
        }

        // Try type-based pool first
        @SuppressWarnings("unchecked")
        ObjectPool<GraphicsInstance> typePool = (ObjectPool<GraphicsInstance>) typePools.get(instance.getClass());
        if (typePool != null) {
            typePool.returnObject(instance);
            return;
        }

        // Check if instance has pooling hint
        if (instance instanceof PoolableGraphicsInstance poolable) {
            Identifier poolName = poolable.getPoolIdentifier();
            ObjectPool<GraphicsInstance> namedPool = namedPools.get(poolName);
            if (namedPool != null) {
                namedPool.returnObject(instance);
                return;
            }
        }

        // If no pool found, instance will be garbage collected normally
    }

    /**
     * Configure pooling behavior
     */
    public void setPoolingEnabled(boolean enabled) {
        this.poolingEnabled = enabled;
    }

    public boolean isPoolingEnabled() {
        return poolingEnabled;
    }

    /**
     * Clear all pools
     */
    public void clearAllPools() {
        typePools.values().forEach(ObjectPool::clear);
        namedPools.values().forEach(ObjectPool::clear);
    }

    /**
     * Get pool statistics
     */
    public PoolManagerStats getStats() {
        int totalPools = typePools.size() + namedPools.size();
        int totalObjectsPooled = typePools.values().stream().mapToInt(ObjectPool::size).sum() +
                namedPools.values().stream().mapToInt(ObjectPool::size).sum();

        return new PoolManagerStats(totalPools, totalObjectsPooled, poolingEnabled);
    }

    public record PoolManagerStats(int totalPools, int totalObjectsPooled, boolean poolingEnabled) {
        @Override
        public String toString() {
            return String.format("InstancePoolManager[pools=%d, objects=%d, enabled=%s]",
                    totalPools, totalObjectsPooled, poolingEnabled);
        }
    }
}