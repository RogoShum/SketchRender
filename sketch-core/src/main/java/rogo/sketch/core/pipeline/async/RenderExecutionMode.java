package rogo.sketch.core.pipeline.async;

/**
 * Enumeration of available render execution modes
 */
public enum RenderExecutionMode {
    /**
     * Synchronous execution - all operations on main thread
     */
    SYNC,
    
    /**
     * Asynchronous execution - operations distributed across threads
     */
    ASYNC,
    
    /**
     * Adaptive execution - automatically choose based on workload
     */
    ADAPTIVE,
    
    /**
     * Hybrid execution - mix of sync and async based on operation type
     */
    HYBRID
}