package rogo.sketch.core.backend;

/**
 * Backend-owned thread/context lifecycle boundary.
 */
public interface BackendThreadContext {
    BackendThreadContext NO_OP = new BackendThreadContext() {
    };

    default void registerMainThread() {
    }

    default boolean isMainThread() {
        return true;
    }

    default void assertMainThread(String caller) {
    }

    default void assertRenderContext(String caller) {
    }

    default void initializeWorkerLane(BackendWorkerLane lane) {
    }

    default void destroyWorkerLane(BackendWorkerLane lane) {
    }

    default void onWorkerLaneStart(BackendWorkerLane lane) {
    }

    default void onWorkerLaneEnd(BackendWorkerLane lane) {
    }
}
