package rogo.sketch.backend.opengl;

import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.backend.AsyncGpuCompletion;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendResourceRegistry;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.backend.BackendThreadContext;
import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.core.backend.CommandEncoderFactory;
import rogo.sketch.core.backend.IndirectDrawService;
import rogo.sketch.core.backend.RenderDevice;
import rogo.sketch.core.backend.RuntimeDebugToggles;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;

import java.util.List;

final class OpenGLRenderDevice implements RenderDevice, BackendThreadContext {
    private final BackendKind kind;
    private final GraphicsAPI api;
    private final long mainWindowHandle;
    private final BackendCapabilities capabilities;
    private final BackendShaderProgramCache shaderProgramCache;
    private final BackendResourceRegistry resourceRegistry;
    private final BackendStateApplier stateApplier;
    private final BackendFrameExecutor frameExecutor;
    private final CommandEncoderFactory commandEncoderFactory;
    private final IndirectDrawService indirectDrawService;

    OpenGLRenderDevice(
            BackendKind kind,
            GraphicsAPI api,
            long mainWindowHandle,
            BackendCapabilities capabilities,
            BackendShaderProgramCache shaderProgramCache,
            BackendResourceRegistry resourceRegistry,
            BackendStateApplier stateApplier,
            BackendFrameExecutor frameExecutor) {
        this.kind = kind;
        this.api = api;
        this.mainWindowHandle = mainWindowHandle;
        this.capabilities = capabilities;
        this.shaderProgramCache = shaderProgramCache;
        this.resourceRegistry = resourceRegistry;
        this.stateApplier = stateApplier;
        this.frameExecutor = frameExecutor;
        this.commandEncoderFactory = new OpenGLCommandEncoderFactory();
        this.indirectDrawService = new OpenGLCountedIndirectDraw();
    }

    BackendKind kind() {
        return kind;
    }

    @Override
    public BackendCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return frameExecutor;
    }

    @Override
    public IndirectDrawService indirectDrawService() {
        return indirectDrawService;
    }

    @Override
    public BackendShaderProgramCache shaderProgramCache() {
        return shaderProgramCache;
    }

    @Override
    public BackendResourceRegistry resourceRegistry() {
        return resourceRegistry;
    }

    @Override
    public BackendStateApplier stateApplier() {
        return stateApplier;
    }

    @Override
    public CommandEncoderFactory commandEncoderFactory() {
        return commandEncoderFactory;
    }

    BackendPacketHandlerRegistry<OpenGLPacketHandler> packetHandlerRegistry() {
        return ((OpenGLFrameExecutor) frameExecutor).packetHandlerRegistry();
    }

    @Override
    public boolean supportsGeometryMaterialization() {
        return true;
    }

    <C extends RenderContext> boolean installGeometryUploads(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        OpenGLGeometryMaterializer.installExecutionGeometryBindings(pipeline, executionPlan, uploadGeometryData);
        return true;
    }

    @Override
    public <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        OpenGLGeometryMaterializer.installImmediateGeometryBindings(pipeline, pipelineType, postProcessors);
        return true;
    }

    @Override
    public <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
        OpenGLGeometryMaterializer.materializePendingGeometryResources(pipeline);
    }

    @Override
    public void registerMainThread() {
        api.registerMainThread();
    }

    @Override
    public boolean isMainThread() {
        return api.isMainThread();
    }

    @Override
    public void assertMainThread(String caller) {
        api.assertMainThread(caller);
    }

    @Override
    public void assertRenderContext(String caller) {
        api.assertGLContext(caller);
    }

    @Override
    public void initializeWorkerLane(BackendWorkerLane lane) {
        if (!capabilities.workerLanesSupported()) {
            return;
        }
        try {
            switch (lane) {
                case RENDER_ASYNC -> api.initRenderWorkerContext(mainWindowHandle);
                case TICK_ASYNC -> api.initTickWorkerContext(mainWindowHandle);
                case UPLOAD_ASYNC -> api.initUploadWorkerContext(mainWindowHandle);
                case COMPUTE_ASYNC -> api.initComputeWorkerContext(mainWindowHandle);
                case OFFSCREEN_GRAPHICS_ASYNC -> api.initOffscreenGraphicsWorkerContext(mainWindowHandle);
            }
        } catch (RuntimeException exception) {
            RuntimeDebugToggles.setGlAsyncGpuWorkersDisabled(true);
            SketchDiagnostics.get().warn(
                    "opengl-worker",
                    "Failed to create shared GL worker context for lane " + lane
                            + "; async GPU packets will fall back to inline execution",
                    exception);
        }
    }

    @Override
    public void destroyWorkerLane(BackendWorkerLane lane) {
        if (!capabilities.workerLanesSupported()) {
            return;
        }
        switch (lane) {
            case RENDER_ASYNC -> api.destroyRenderWorkerContext();
            case TICK_ASYNC -> api.destroyTickWorkerContext();
            case UPLOAD_ASYNC -> api.destroyUploadWorkerContext();
            case COMPUTE_ASYNC -> api.destroyComputeWorkerContext();
            case OFFSCREEN_GRAPHICS_ASYNC -> api.destroyOffscreenGraphicsWorkerContext();
        }
    }

    @Override
    public void onWorkerLaneStart(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadStart();
            case TICK_ASYNC -> api.onTickWorkerThreadStart();
            case UPLOAD_ASYNC -> api.onUploadWorkerThreadStart();
            case COMPUTE_ASYNC -> api.onComputeWorkerThreadStart();
            case OFFSCREEN_GRAPHICS_ASYNC -> api.onOffscreenGraphicsWorkerThreadStart();
        }
    }

    @Override
    public void onWorkerLaneEnd(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadEnd();
            case TICK_ASYNC -> api.onTickWorkerThreadEnd();
            case UPLOAD_ASYNC -> api.onUploadWorkerThreadEnd();
            case COMPUTE_ASYNC -> api.onComputeWorkerThreadEnd();
            case OFFSCREEN_GRAPHICS_ASYNC -> api.onOffscreenGraphicsWorkerThreadEnd();
        }
    }

    @Override
    public <C extends RenderContext> AsyncGpuCompletion submitAsyncPackets(
            GraphicsPipeline<C> pipeline,
            List<RenderPacket> packets,
            C context) {
        RenderDevice.super.submitAsyncPackets(pipeline, packets, context);
        long fence = api.createFenceSync();
        api.flush();
        return new OpenGLAsyncFenceCompletion(api, fence);
    }
}
