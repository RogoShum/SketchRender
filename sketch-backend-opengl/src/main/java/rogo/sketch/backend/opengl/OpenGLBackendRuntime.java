package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendResourceResolver;
import rogo.sketch.core.backend.BackendResourceInstaller;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.backend.opengl.driver.GLRuntimeFlags;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

import java.util.Objects;

/**
 * Formal OpenGL backend runtime owner.
 */
public final class OpenGLBackendRuntime implements BackendRuntime {
    private final BackendKind kind;
    private final GraphicsAPI api;
    private final long mainWindowHandle;
    private final BackendCapabilities capabilities;
    private final BackendShaderProgramCache shaderProgramCache;
    private final BackendResourceResolver resourceResolver;
    private final BackendResourceInstaller resourceInstaller;
    private final BackendStateApplier stateApplier;
    private final BackendFrameExecutor frameExecutor;

    public OpenGLBackendRuntime(BackendKind kind, GraphicsAPI api, long mainWindowHandle) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.api = Objects.requireNonNull(api, "api");
        this.mainWindowHandle = mainWindowHandle;
        this.capabilities = new BackendCapabilities(
                GLRuntimeFlags.GL_WORKER_ENABLED,
                GLRuntimeFlags.allowUploadWorker(),
                GLRuntimeFlags.allowComputeWorker(),
                false);
        this.resourceResolver = new OpenGLBackendResourceResolver();
        this.shaderProgramCache = new OpenGLBackendShaderProgramCache(api);
        this.resourceInstaller = new OpenGLBackendResourceInstaller(api, (OpenGLBackendResourceResolver) resourceResolver);
        this.stateApplier = new OpenGLStateApplier(api, resourceResolver, new NativeOpenGLStateAccess(api));
        this.frameExecutor = new OpenGLFrameExecutor(api, resourceResolver);
    }

    @Override
    public String backendName() {
        return api.getAPIName();
    }

    @Override
    public BackendKind kind() {
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

    public BackendPacketHandlerRegistry<OpenGLPacketHandler> packetHandlerRegistry() {
        return ((OpenGLFrameExecutor) frameExecutor).packetHandlerRegistry();
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
    public BackendResourceInstaller resourceInstaller() {
        return resourceInstaller;
    }

    @Override
    public BackendStateApplier stateApplier() {
        return stateApplier;
    }

    @Override
    public boolean supportsGeometryMaterialization() {
        return true;
    }

    @Override
    public <C extends RenderContext> boolean installGeometryUploads(
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
        switch (lane) {
            case RENDER_ASYNC -> api.initRenderWorkerContext(mainWindowHandle);
            case TICK_ASYNC -> api.initTickWorkerContext(mainWindowHandle);
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
        }
    }

    @Override
    public void onWorkerLaneStart(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadStart();
            case TICK_ASYNC -> api.onTickWorkerThreadStart();
        }
    }

    @Override
    public void onWorkerLaneEnd(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadEnd();
            case TICK_ASYNC -> api.onTickWorkerThreadEnd();
        }
    }
}

