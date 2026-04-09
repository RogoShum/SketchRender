package rogo.sketch.vanilla.backend.opengl;

import rogo.sketch.core.backend.*;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

final class LegacyMinecraftOpenGLBackendRuntime implements BackendRuntime {
    private final BackendRuntime delegate;
    private final BackendStateApplier stateApplier;
    private final BackendFrameExecutor frameExecutor;

    LegacyMinecraftOpenGLBackendRuntime(
            BackendRuntime delegate,
            BackendStateApplier stateApplier,
            BackendFrameExecutor frameExecutor) {
        this.delegate = delegate;
        this.stateApplier = stateApplier;
        this.frameExecutor = frameExecutor;
    }

    @Override
    public String backendName() {
        return delegate.backendName();
    }

    @Override
    public BackendKind kind() {
        return delegate.kind();
    }

    @Override
    public BackendCapabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return frameExecutor;
    }

    @Override
    public BackendPacketCompiler packetCompiler() {
        return delegate.packetCompiler();
    }

    @Override
    public BackendShaderProgramCache shaderProgramCache() {
        return delegate.shaderProgramCache();
    }

    @Override
    public BackendResourceInstaller resourceInstaller() {
        return delegate.resourceInstaller();
    }

    @Override
    public BackendResourceResolver resourceResolver() {
        return delegate.resourceResolver();
    }

    @Override
    public BackendStateApplier stateApplier() {
        return stateApplier;
    }

    @Override
    public boolean supportsGeometryMaterialization() {
        return delegate.supportsGeometryMaterialization();
    }

    @Override
    public <C extends RenderContext> boolean installGeometryUploads(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        return delegate.installGeometryUploads(pipeline, executionPlan, uploadGeometryData);
    }

    @Override
    public <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        return delegate.installImmediateGeometryBindings(pipeline, pipelineType, postProcessors);
    }

    @Override
    public <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
        delegate.materializePendingGeometryResources(pipeline);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public void registerMainThread() {
        delegate.registerMainThread();
    }

    @Override
    public boolean isMainThread() {
        return delegate.isMainThread();
    }

    @Override
    public void assertMainThread(String caller) {
        delegate.assertMainThread(caller);
    }

    @Override
    public void assertRenderContext(String caller) {
        delegate.assertRenderContext(caller);
    }

    @Override
    public void installExecutionPlan(FrameExecutionPlan executionPlan) {
        delegate.installExecutionPlan(executionPlan);
    }

    @Override
    public void initializeWorkerLane(BackendWorkerLane lane) {
        delegate.initializeWorkerLane(lane);
    }

    @Override
    public void destroyWorkerLane(BackendWorkerLane lane) {
        delegate.destroyWorkerLane(lane);
    }

    @Override
    public void onWorkerLaneStart(BackendWorkerLane lane) {
        delegate.onWorkerLaneStart(lane);
    }

    @Override
    public void onWorkerLaneEnd(BackendWorkerLane lane) {
        delegate.onWorkerLaneEnd(lane);
    }
}
