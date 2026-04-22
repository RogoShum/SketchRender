package rogo.sketch.core.backend;

public interface GpuFence extends AutoCloseable {
    GpuFence NO_OP = new GpuFence() {
    };

    default boolean isComplete() {
        return true;
    }

    default void await() {
    }

    @Override
    default void close() {
    }
}
