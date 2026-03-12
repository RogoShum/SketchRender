package rogo.sketch.core.pipeline.kernel;

/**
 * Defines the thread-domain contract for pipeline operations.
 * <p>
 * Every pass, method, or callback in the pipeline must declare which thread domain
 * it belongs to. This enables both compile-time documentation and runtime guards.
 * </p>
 */
public enum ThreadDomain {
    /**
     * Must execute on the main/render thread (GL context available).
     */
    SYNC,

    /**
     * Must execute on a worker thread (no GL context).
     */
    ASYNC,

    /**
     * May execute on any thread. The method is either pure-CPU,
     * operates on immutable data, or uses explicit synchronization.
     */
    ANY
}

