package rogo.sketch.core.backend;

/**
 * Minimal backend-agnostic command recording surface for submit-time work.
 * <p>
 * The first version intentionally stays small and focuses on the operations
 * that the commit pipeline and future upload/dispatch paths need in common.
 * Individual backends may override only the operations they can currently
 * encode while the rest remain harmless no-ops.
 * </p>
 */
public interface CommandRecorder extends AutoCloseable {
    default void uploadBuffer(BackendStorageBuffer target, long sourceAddress, long byteCount) {
        if (target == null) {
            return;
        }
        if (sourceAddress > 0L && byteCount > 0L) {
            target.upload(sourceAddress, byteCount);
            return;
        }
        target.upload();
    }

    default void copyBuffer(
            BackendInstalledBuffer source,
            long sourceOffsetBytes,
            BackendInstalledBuffer target,
            long targetOffsetBytes,
            long byteCount) {
    }

    default void clearCounter(BackendCounterBuffer counterBuffer, int value) {
    }

    default void clearBuffer(
            BackendInstalledBuffer buffer,
            long offsetBytes,
            long byteCount,
            int clearValue) {
    }

    default void bufferBarrier() {
    }

    default void imageBarrier() {
    }

    default void dispatch(int groupCountX, int groupCountY, int groupCountZ) {
    }

    default void submit() {
    }

    @Override
    default void close() {
    }
}
