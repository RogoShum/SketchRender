package rogo.sketch.render.pool;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.util.Identifier;

/**
 * Interface for GraphicsInstance objects that support pooling with named pools
 */
public interface PoolableGraphicsInstance extends GraphicsInstance, Poolable {
    /**
     * Get the pool identifier for this instance type
     * Used to determine which named pool this instance belongs to
     */
    Identifier getPoolIdentifier();
    
    /**
     * Configure the instance with new parameters after borrowing from pool
     */
    void configure(Object... params);
}
