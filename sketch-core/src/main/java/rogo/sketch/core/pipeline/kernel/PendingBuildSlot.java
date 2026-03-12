package rogo.sketch.core.pipeline.kernel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Kernel-owned mailbox for cross-frame async build handoff.
 * Producer: async build worker. Consumer: sync commit pass.
 */
public final class PendingBuildSlot {
    private final AtomicReference<BuildResult> readyResult = new AtomicReference<>();

    public void publish(BuildResult result) {
        readyResult.set(result);
    }

    public BuildResult consume() {
        return readyResult.getAndSet(null);
    }

    public BuildResult peek() {
        return readyResult.get();
    }
}