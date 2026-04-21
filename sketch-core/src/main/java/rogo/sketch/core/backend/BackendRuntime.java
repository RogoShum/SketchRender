package rogo.sketch.core.backend;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.packet.RenderPacket;

import java.util.List;

public interface BackendRuntime {
    String backendName();

    BackendKind kind();

    BackendCapabilities capabilities();

    BackendFrameExecutor frameExecutor();

    default RenderDevice renderDevice() {
        return new RenderDevice() {
            @Override
            public BackendCapabilities capabilities() {
                return BackendCapabilities.NONE;
            }

            @Override
            public BackendFrameExecutor frameExecutor() {
                return BackendRuntime.this.frameExecutor();
            }

            @Override
            public BackendShaderProgramCache shaderProgramCache() {
                return BackendShaderProgramCache.NO_OP;
            }

            @Override
            public BackendResourceResolver resourceResolver() {
                return BackendResourceResolver.NO_OP;
            }

            @Override
            public BackendStateApplier stateApplier() {
                return BackendStateApplier.NO_OP;
            }

            @Override
            public CommandRecorderFactory commandRecorderFactory() {
                return CommandRecorderFactory.noOp();
            }
        };
    }

    default ResourceAllocator resourceAllocator() {
        return ResourceAllocator.NO_OP;
    }

    default SubmissionScheduler submissionScheduler() {
        return SubmissionScheduler.NO_OP;
    }

    default BackendPacketCompiler packetCompiler() {
        return new BackendPacketCompiler() {
        };
    }

    default BackendShaderProgramCache shaderProgramCache() {
        return renderDevice().shaderProgramCache();
    }

    default BackendResourceInstaller resourceInstaller() {
        return resourceAllocator();
    }

    default BackendResourceResolver resourceResolver() {
        return renderDevice().resourceResolver();
    }

    default BackendStateApplier stateApplier() {
        return renderDevice().stateApplier();
    }

    default CommandRecorderFactory commandRecorderFactory() {
        return renderDevice().commandRecorderFactory();
    }

    default boolean supportsGeometryMaterialization() {
        return false;
    }

    default <C extends RenderContext> boolean installGeometryUploads(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        return false;
    }

    default <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        return false;
    }

    default void installImmediateResourceBindings(List<RenderPacket> packets) {
    }

    default <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
    }

    default void shutdown() {
    }

    default void registerMainThread() {
    }

    default boolean isMainThread() {
        return true;
    }

    default void assertMainThread(String caller) {
    }

    default void assertRenderContext(String caller) {
    }

    default void installExecutionPlan(FrameExecutionPlan executionPlan) {
        resourceAllocator().installExecutionPlan(executionPlan, 0L, submissionScheduler().framesInFlight());
        submissionScheduler().installExecutionPlan(executionPlan);
    }

    default void initializeWorkerLane(BackendWorkerLane lane) {
    }

    default void destroyWorkerLane(BackendWorkerLane lane) {
    }

    default void onWorkerLaneStart(BackendWorkerLane lane) {
    }

    default void onWorkerLaneEnd(BackendWorkerLane lane) {
    }

    default <C extends RenderContext> AsyncGpuCompletion submitAsyncPackets(
            GraphicsPipeline<C> pipeline,
            List<RenderPacket> packets,
            C context) {
        if (pipeline == null || packets == null || packets.isEmpty() || context == null) {
            return AsyncGpuCompletion.completed();
        }
        RenderStateManager manager = new RenderStateManager();
        for (RenderPacket packet : packets) {
            if (packet != null) {
                frameExecutor().executeImmediate(pipeline, packet, manager, context);
            }
        }
        return AsyncGpuCompletion.completed();
    }
}

