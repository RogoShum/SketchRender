package rogo.sketch.core.backend;

import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

/**
 * Backend-facing frame scheduling service. Thin backends may implement a no-op
 * scheduler; explicit APIs such as Vulkan use it to own frame slots,
 * acquire/submit/present and deferred destruction.
 */
public interface SubmissionScheduler {
    SubmissionScheduler NO_OP = new SubmissionScheduler() {
    };

    default void installExecutionPlan(FrameExecutionPlan plan) {
    }

    default int framesInFlight() {
        return 1;
    }

    default boolean drawFrame() {
        return true;
    }

    default void markFramebufferResized() {
    }

    default void drainDeferredDestruction() {
    }

    default void shutdown() {
    }
}
