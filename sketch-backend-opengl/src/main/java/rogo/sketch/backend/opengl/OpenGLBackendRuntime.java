package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.core.backend.RenderDevice;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.backend.SubmissionScheduler;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.backend.BackendThreadContext;
import rogo.sketch.backend.opengl.driver.GLRuntimeFlags;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;

import java.util.Objects;

/**
 * Formal OpenGL backend runtime owner.
 */
public final class OpenGLBackendRuntime implements BackendRuntime {
    private final GraphicsAPI api;
    private final OpenGLRenderDevice renderDevice;
    private final OpenGLResourceAllocator resourceAllocator;
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
        BackendStateApplier stateApplier = new OpenGLStateApplier(api, resourceAllocator, new NativeOpenGLStateAccess(api));
        BackendFrameExecutor frameExecutor = new OpenGLFrameExecutor(api, resourceAllocator);
        this.submissionScheduler = new OpenGLSubmissionScheduler();
        this.renderDevice = new OpenGLRenderDevice(
                validatedKind,
                api,
                mainWindowHandle,
                capabilities,
                shaderProgramCache,
                resourceAllocator,
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
    public BackendThreadContext threadContext() {
        return renderDevice;
    }

    @Override
    public BackendCapabilities capabilities() {
        return renderDevice.capabilities();
    }

    public BackendPacketHandlerRegistry<OpenGLPacketHandler> packetHandlerRegistry() {
        return renderDevice.packetHandlerRegistry();
    }

    @Override
    public void shutdown() {
        resourceAllocator.shutdown();
    }
}

