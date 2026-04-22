package rogo.sketch.core.backend;

public interface BackendRuntime {
    String backendName();

    BackendKind kind();

    BackendCapabilities capabilities();

    RenderDevice renderDevice();

    default ResourceAllocator resourceAllocator() {
        return ResourceAllocator.NO_OP;
    }

    default SubmissionScheduler submissionScheduler() {
        return SubmissionScheduler.NO_OP;
    }

    default void shutdown() {
    }

    default BackendThreadContext threadContext() {
        return BackendThreadContext.NO_OP;
    }

    default QueueRouter queueRouter() {
        return QueueRouter.NO_OP;
    }
}

