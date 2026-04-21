package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendResourceResolver;
import rogo.sketch.core.backend.BackendResourceInstaller;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.core.backend.AsyncGpuCompletion;
import rogo.sketch.core.backend.RenderDevice;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.backend.SubmissionScheduler;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.core.backend.CommandRecorderFactory;
import rogo.sketch.backend.opengl.driver.GLRuntimeFlags;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.packet.RenderPacket;

import java.util.List;
import java.util.Objects;

/**
 * Formal OpenGL backend runtime owner.
 */
public final class OpenGLBackendRuntime implements BackendRuntime {
    private final GraphicsAPI api;
    private final OpenGLRenderDevice renderDevice;
    private final OpenGLResourceAllocator resourceAllocator;
    private final BackendResourceInstaller resourceInstaller;
    private final OpenGLSubmissionScheduler submissionScheduler;

    public OpenGLBackendRuntime(BackendKind kind, GraphicsAPI api, long mainWindowHandle) {
        BackendKind validatedKind = Objects.requireNonNull(kind, "kind");
        this.api = Objects.requireNonNull(api, "api");
        BackendCapabilities capabilities = new BackendCapabilities(
                GLRuntimeFlags.GL_WORKER_ENABLED,
                GLRuntimeFlags.allowUploadWorker(),
                GLRuntimeFlags.allowComputeWorker(),
                GLRuntimeFlags.GL_WORKER_ENABLED,
                false);
        OpenGLBackendResourceResolver resourceResolver = new OpenGLBackendResourceResolver();
        BackendShaderProgramCache shaderProgramCache = new OpenGLBackendShaderProgramCache(api);
        this.resourceAllocator = new OpenGLResourceAllocator(api, resourceResolver);
        this.resourceInstaller = new OpenGLBackendResourceInstaller(resourceAllocator);
        BackendStateApplier stateApplier = new OpenGLStateApplier(api, resourceResolver, new NativeOpenGLStateAccess(api));
        BackendFrameExecutor frameExecutor = new OpenGLFrameExecutor(api, resourceResolver);
        this.submissionScheduler = new OpenGLSubmissionScheduler();
        this.renderDevice = new OpenGLRenderDevice(
                validatedKind,
                api,
                mainWindowHandle,
                capabilities,
                shaderProgramCache,
                resourceResolver,
                stateApplier,
                frameExecutor);
    }

    @Override
    public String backendName() {
        return api.getAPIName();
    }

    @Override
    public BackendKind kind() {
        return renderDevice.kind();
    }

    @Override
    public RenderDevice renderDevice() {
        return renderDevice;
    }

    @Override
    public ResourceAllocator resourceAllocator() {
        return resourceAllocator;
    }

    @Override
    public SubmissionScheduler submissionScheduler() {
        return submissionScheduler;
    }

    @Override
    public BackendCapabilities capabilities() {
        return renderDevice.capabilities();
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return renderDevice.frameExecutor();
    }

    public BackendPacketHandlerRegistry<OpenGLPacketHandler> packetHandlerRegistry() {
        return renderDevice.packetHandlerRegistry();
    }

    @Override
    public BackendShaderProgramCache shaderProgramCache() {
        return renderDevice.shaderProgramCache();
    }

    @Override
    public BackendResourceResolver resourceResolver() {
        return renderDevice.resourceResolver();
    }

    @Override
    public BackendResourceInstaller resourceInstaller() {
        return resourceInstaller;
    }

    @Override
    public BackendStateApplier stateApplier() {
        return renderDevice.stateApplier();
    }

    @Override
    public CommandRecorderFactory commandRecorderFactory() {
        return renderDevice.commandRecorderFactory();
    }

    @Override
    public boolean supportsGeometryMaterialization() {
        return renderDevice.supportsGeometryMaterialization();
    }

    @Override
    public <C extends RenderContext> boolean installGeometryUploads(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        return resourceAllocator.installExecutionPlan(
                pipeline,
                executionPlan,
                0L,
                submissionScheduler.framesInFlight(),
                uploadGeometryData);
    }

    @Override
    public <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        return renderDevice.installImmediateGeometryBindings(pipeline, pipelineType, postProcessors);
    }

    @Override
    public <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
        renderDevice.materializePendingGeometryResources(pipeline);
    }

    @Override
    public void installExecutionPlan(FrameExecutionPlan executionPlan) {
        FrameExecutionPlan nextExecutionPlan = executionPlan != null ? executionPlan : FrameExecutionPlan.empty();
        resourceAllocator.installExecutionPlan(nextExecutionPlan, 0L, submissionScheduler.framesInFlight());
        submissionScheduler.installExecutionPlan(nextExecutionPlan);
    }

    @Override
    public void shutdown() {
        resourceAllocator.shutdown();
    }

    @Override
    public void registerMainThread() {
        renderDevice.registerMainThread();
    }

    @Override
    public boolean isMainThread() {
        return renderDevice.isMainThread();
    }

    @Override
    public void assertMainThread(String caller) {
        renderDevice.assertMainThread(caller);
    }

    @Override
    public void assertRenderContext(String caller) {
        renderDevice.assertRenderContext(caller);
    }

    @Override
    public void initializeWorkerLane(BackendWorkerLane lane) {
        renderDevice.initializeWorkerLane(lane);
    }

    @Override
    public void destroyWorkerLane(BackendWorkerLane lane) {
        renderDevice.destroyWorkerLane(lane);
    }

    @Override
    public void onWorkerLaneStart(BackendWorkerLane lane) {
        renderDevice.onWorkerLaneStart(lane);
    }

    @Override
    public void onWorkerLaneEnd(BackendWorkerLane lane) {
        renderDevice.onWorkerLaneEnd(lane);
    }

    @Override
    public <C extends RenderContext> AsyncGpuCompletion submitAsyncPackets(
            GraphicsPipeline<C> pipeline,
            List<RenderPacket> packets,
            C context) {
        BackendRuntime.super.submitAsyncPackets(pipeline, packets, context);
        long fence = api.createFenceSync();
        api.flush();
        return new OpenGLAsyncFenceCompletion(api, fence);
    }
}

