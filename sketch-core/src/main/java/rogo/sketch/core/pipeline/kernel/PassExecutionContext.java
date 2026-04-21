package rogo.sketch.core.pipeline.kernel;

/**
 * Execution-time access to frame resource handles.
 */
public final class PassExecutionContext {
    private final PipelineKernel<?> kernel;
    private final String moduleId;
    private final String passId;
    private final LifecyclePhase phase;
    private final long frameEpoch;
    private final long logicTickEpoch;

    PassExecutionContext(
            PipelineKernel<?> kernel,
            String moduleId,
            String passId,
            LifecyclePhase phase,
            long frameEpoch,
            long logicTickEpoch) {
        this.kernel = kernel;
        this.moduleId = moduleId;
        this.passId = passId;
        this.phase = phase;
        this.frameEpoch = frameEpoch;
        this.logicTickEpoch = logicTickEpoch;
    }

    public String moduleId() {
        return moduleId;
    }

    public String passId() {
        return passId;
    }

    public LifecyclePhase phase() {
        return phase;
    }

    public long frameEpoch() {
        return frameEpoch;
    }

    public long logicTickEpoch() {
        return logicTickEpoch;
    }

    public <T> PublishedFrameResource<T> publish(FrameResourceHandle<T> handle, long epoch, T payload) {
        return kernel.publishFrameResource(handle, epoch, payload);
    }

    public <T> PublishedFrameResource<T> peek(FrameResourceHandle<T> handle) {
        return kernel.peekFrameResource(handle);
    }

    public <T> PublishedFrameResource<T> consume(FrameResourceHandle<T> handle) {
        return kernel.consumeFrameResource(handle);
    }
}
