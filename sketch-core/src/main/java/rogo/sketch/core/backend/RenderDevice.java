package rogo.sketch.core.backend;

import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;

import java.util.List;

/**
 * Stable backend device-facing service boundary used by phase-3 backend
 * refactors. Runtime facades may still expose legacy getters, but the actual
 * backend service graph should hang off this contract.
 */
public interface RenderDevice {
    BackendCapabilities capabilities();

    BackendFrameExecutor frameExecutor();

    default IndirectDrawService indirectDrawService() {
        return IndirectDrawService.UNSUPPORTED;
    }

    BackendShaderProgramCache shaderProgramCache();

    BackendResourceRegistry resourceRegistry();

    BackendStateApplier stateApplier();

    CommandEncoderFactory commandEncoderFactory();

    default boolean supportsGeometryMaterialization() {
        return false;
    }

    default <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        return false;
    }

    default <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
    }

    /**
     * Fallback immediate async-submission path for backends without their own
     * queue/context ownership. Production backends should override this when
     * they need native fences, command pools, queue routing, or worker-context
     * synchronization.
     */
    default <C extends RenderContext> AsyncGpuCompletion submitAsyncPackets(
            GraphicsPipeline<C> pipeline,
            List<RenderPacket> packets,
            C context) {
        if (pipeline == null || packets == null || packets.isEmpty() || context == null) {
            return AsyncGpuCompletion.completed();
        }
        RenderStateManager manager = new RenderStateManager(pipeline.resourceManager());
        for (RenderPacket packet : packets) {
            if (packet != null) {
                frameExecutor().executeImmediate(pipeline, packet, manager, context);
            }
        }
        return AsyncGpuCompletion.completed();
    }
}
