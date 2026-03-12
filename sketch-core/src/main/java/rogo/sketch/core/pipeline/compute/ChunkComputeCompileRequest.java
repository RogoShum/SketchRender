package rogo.sketch.core.pipeline.compute;

/**
 * Future-facing request model for async chunk mesh compute compilation.
 */
public record ChunkComputeCompileRequest(
        long chunkKey,
        int priority,
        long enqueueNanos
) implements Comparable<ChunkComputeCompileRequest> {
    @Override
    public int compareTo(ChunkComputeCompileRequest other) {
        int byPriority = Integer.compare(other.priority, this.priority);
        if (byPriority != 0) {
            return byPriority;
        }
        return Long.compare(this.enqueueNanos, other.enqueueNanos);
    }
}


