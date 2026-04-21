package rogo.sketch.backend.opengl;

import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendCountedIndirectDraw;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendResourceResolver;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.core.backend.CommandRecorderFactory;
import rogo.sketch.core.backend.RenderDevice;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

final class OpenGLRenderDevice implements RenderDevice {
    private final BackendKind kind;
    private final GraphicsAPI api;
    private final long mainWindowHandle;
    private final BackendCapabilities capabilities;
    private final BackendShaderProgramCache shaderProgramCache;
    private final BackendResourceResolver resourceResolver;
    private final BackendStateApplier stateApplier;
    private final BackendFrameExecutor frameExecutor;
    private final CommandRecorderFactory commandRecorderFactory;
    private final BackendCountedIndirectDraw countedIndirectDraw;

    OpenGLRenderDevice(
            BackendKind kind,
            GraphicsAPI api,
            long mainWindowHandle,
            BackendCapabilities capabilities,
            BackendShaderProgramCache shaderProgramCache,
            BackendResourceResolver resourceResolver,
            BackendStateApplier stateApplier,
            BackendFrameExecutor frameExecutor) {
        this.kind = kind;
        this.api = api;
        this.mainWindowHandle = mainWindowHandle;
        this.capabilities = capabilities;
        this.shaderProgramCache = shaderProgramCache;
        this.resourceResolver = resourceResolver;
        this.stateApplier = stateApplier;
        this.frameExecutor = frameExecutor;
        this.commandRecorderFactory = new OpenGLCommandRecorderFactory();
        this.countedIndirectDraw = new OpenGLCountedIndirectDraw();
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
    public BackendCountedIndirectDraw countedIndirectDraw() {
        return countedIndirectDraw;
    }

    @Override
    public BackendShaderProgramCache shaderProgramCache() {
        return shaderProgramCache;
    }

    @Override
    public BackendResourceResolver resourceResolver() {
        return resourceResolver;
    }

    @Override
    public BackendStateApplier stateApplier() {
        return stateApplier;
    }

    @Override
    public CommandRecorderFactory commandRecorderFactory() {
        return commandRecorderFactory;
    }

    BackendPacketHandlerRegistry<OpenGLPacketHandler> packetHandlerRegistry() {
        return ((OpenGLFrameExecutor) frameExecutor).packetHandlerRegistry();
    }

    boolean supportsGeometryMaterialization() {
        return true;
    }

    <C extends RenderContext> boolean installGeometryUploads(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        OpenGLGeometryMaterializer.installExecutionGeometryBindings(pipeline, executionPlan, uploadGeometryData);
        return true;
    }

    <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        OpenGLGeometryMaterializer.installImmediateGeometryBindings(pipeline, pipelineType, postProcessors);
        return true;
    }

    <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
        OpenGLGeometryMaterializer.materializePendingGeometryResources(pipeline);
    }

    void registerMainThread() {
        api.registerMainThread();
    }

    boolean isMainThread() {
        return api.isMainThread();
    }

    void assertMainThread(String caller) {
        api.assertMainThread(caller);
    }

    void assertRenderContext(String caller) {
        api.assertGLContext(caller);
    }

    void initializeWorkerLane(BackendWorkerLane lane) {
        if (!capabilities.workerLanesSupported()) {
            return;
        }
        switch (lane) {
            case RENDER_ASYNC -> api.initRenderWorkerContext(mainWindowHandle);
            case TICK_ASYNC -> api.initTickWorkerContext(mainWindowHandle);
            case UPLOAD_ASYNC -> api.initUploadWorkerContext(mainWindowHandle);
            case COMPUTE_ASYNC -> api.initComputeWorkerContext(mainWindowHandle);
            case OFFSCREEN_GRAPHICS_ASYNC -> api.initOffscreenGraphicsWorkerContext(mainWindowHandle);
        }
    }

    void destroyWorkerLane(BackendWorkerLane lane) {
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

    void onWorkerLaneStart(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadStart();
            case TICK_ASYNC -> api.onTickWorkerThreadStart();
            case UPLOAD_ASYNC -> api.onUploadWorkerThreadStart();
            case COMPUTE_ASYNC -> api.onComputeWorkerThreadStart();
            case OFFSCREEN_GRAPHICS_ASYNC -> api.onOffscreenGraphicsWorkerThreadStart();
        }
    }

    void onWorkerLaneEnd(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadEnd();
            case TICK_ASYNC -> api.onTickWorkerThreadEnd();
            case UPLOAD_ASYNC -> api.onUploadWorkerThreadEnd();
            case COMPUTE_ASYNC -> api.onComputeWorkerThreadEnd();
            case OFFSCREEN_GRAPHICS_ASYNC -> api.onOffscreenGraphicsWorkerThreadEnd();
        }
    }
}
