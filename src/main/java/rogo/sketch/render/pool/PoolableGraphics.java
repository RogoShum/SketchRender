package rogo.sketch.render.pool;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.util.KeyId;

/**
 * Interface for GraphicsInstance objects that support pooling with named pools
 */
public interface PoolableGraphics extends Graphics, Poolable {
    /**
     * Get the pool identifier for this instance type
     * Used to determine which named pool this instance belongs to
     */
    KeyId getPoolIdentifier();
    
    /**
     * Configure the instance with new parameters after borrowing from pool
     */
    void configure(Object... params);
}