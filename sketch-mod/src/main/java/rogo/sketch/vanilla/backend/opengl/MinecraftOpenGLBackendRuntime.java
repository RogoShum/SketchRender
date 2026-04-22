package rogo.sketch.vanilla.backend.opengl;

import rogo.sketch.backend.opengl.OpenGLStateAccess;
import rogo.sketch.backend.opengl.OpenGLStateApplier;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.backend.*;

final class MinecraftOpenGLBackendRuntime implements BackendRuntime {
    private final BackendRuntime delegate;
    private final BackendStateApplier stateApplier;
    private final BackendFrameExecutor frameExecutor;

    MinecraftOpenGLBackendRuntime(
            BackendRuntime delegate,
            GraphicsAPI api,
            OpenGLStateAccess stateAccess) {
        this.delegate = delegate;
        this.stateApplier = new OpenGLStateApplier(api, delegate.renderDevice().resourceRegistry(), stateAccess);
        this.frameExecutor = new MinecraftOpenGLFrameExecutor(api, delegate.renderDevice().frameExecutor(), stateAccess);
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
    public RenderDevice renderDevice() {
        RenderDevice renderDevice = delegate.renderDevice();
        return new RenderDevice() {
            @Override
            public BackendCapabilities capabilities() {
                return renderDevice.capabilities();
            }

            @Override
            public BackendFrameExecutor frameExecutor() {
                return frameExecutor;
            }

            @Override
            public IndirectDrawService indirectDrawService() {
                return renderDevice.indirectDrawService();
            }

            @Override
            public BackendShaderProgramCache shaderProgramCache() {
                return renderDevice.shaderProgramCache();
            }

            @Override
            public BackendResourceRegistry resourceRegistry() {
                return renderDevice.resourceRegistry();
            }

            @Override
            public BackendStateApplier stateApplier() {
                return stateApplier;
            }

            @Override
            public CommandEncoderFactory commandEncoderFactory() {
                return renderDevice.commandEncoderFactory();
            }

            @Override
            public boolean supportsGeometryMaterialization() {
                return renderDevice.supportsGeometryMaterialization();
            }

            @Override
            public <C extends rogo.sketch.core.pipeline.RenderContext> boolean installImmediateGeometryBindings(
                    rogo.sketch.core.pipeline.GraphicsPipeline<C> pipeline,
                    rogo.sketch.core.pipeline.PipelineType pipelineType,
                    rogo.sketch.core.pipeline.flow.RenderPostProcessors postProcessors) {
                return renderDevice.installImmediateGeometryBindings(pipeline, pipelineType, postProcessors);
            }

            @Override
            public <C extends rogo.sketch.core.pipeline.RenderContext> void materializePendingGeometryResources(
                    rogo.sketch.core.pipeline.GraphicsPipeline<C> pipeline) {
                renderDevice.materializePendingGeometryResources(pipeline);
            }

            @Override
            public <C extends rogo.sketch.core.pipeline.RenderContext> AsyncGpuCompletion submitAsyncPackets(
                    rogo.sketch.core.pipeline.GraphicsPipeline<C> pipeline,
                    java.util.List<rogo.sketch.core.packet.RenderPacket> packets,
                    C context) {
                return renderDevice.submitAsyncPackets(pipeline, packets, context);
            }
        };
    }

    @Override
    public ResourceAllocator resourceAllocator() {
        return delegate.resourceAllocator();
    }

    @Override
    public SubmissionScheduler submissionScheduler() {
        return delegate.submissionScheduler();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public BackendThreadContext threadContext() {
        return delegate.threadContext();
    }

    @Override
    public QueueRouter queueRouter() {
        return delegate.queueRouter();
    }
}
