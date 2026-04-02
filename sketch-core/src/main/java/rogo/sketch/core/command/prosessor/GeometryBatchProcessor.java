package rogo.sketch.core.command.prosessor;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.*;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.TimerUtil;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Legacy flow facade retained during the StageFlowCompilerFacade migration.
 * <p>
 * New packet compilation structure should be introduced above this layer rather
 * than adding more policy directly into this type.
 */
@Deprecated(forRemoval = false)
public class GeometryBatchProcessor {
    private final PipelineDataStore backendDataRegistry;
    private final VertexResourceManager vertexResourceManager;
    private final RenderFlowRegistry flowRegistry = RenderFlowRegistry.getInstance();

    public GeometryBatchProcessor(VertexResourceManager vertexResourceManager, PipelineDataStore backendDataRegistry) {
        this.backendDataRegistry = backendDataRegistry;
        this.vertexResourceManager = vertexResourceManager;
    }

    /**
     * Create render packets using BatchContainer.
     *
     * @param batchContainer The BatchContainer with pre-organized batches
     * @param flowType       The flow type for strategy selection
     * @param stageId        The stage identifier
     * @param postProcessors Post processors
     * @param <G>            Graphics type
     * @param <I>            InstanceInfo type
     * @return Map of render packets grouped by compiled pipeline state
     */
    @SuppressWarnings("unchecked")
    public <G extends Graphics, I extends InstanceInfo<G>> Map<PipelineStateKey, List<RenderPacket>> createPackets(
            BatchContainer<G, I> batchContainer,
            PipelineType pipelineType,
            RenderFlowType flowType,
            KeyId stageId,
            RenderPostProcessors postProcessors,
            RenderContext context) {

        Optional<RenderFlowStrategy<?, ?>> strategyOpt = flowRegistry.getStrategy(flowType);
        if (strategyOpt.isEmpty()) {
            return Collections.emptyMap();
        }

        RenderFlowStrategy<G, I> strategy = (RenderFlowStrategy<G, I>) strategyOpt.get();
        PacketBuildContext flowContext = new PacketBuildContext(pipelineType, vertexResourceManager, backendDataRegistry);

        TimerUtil.COMMAND_TIMER.start("create command " + flowType);
        Map<PipelineStateKey, List<RenderPacket>> packets = strategy.buildPackets(
                batchContainer, stageId, flowContext, postProcessors, context);
        TimerUtil.COMMAND_TIMER.end("create command " + flowType);

        return packets;
    }

    /**
     * Variant for callsites with wildcard-typed containers.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map<PipelineStateKey, List<RenderPacket>> createPacketsUnchecked(
            BatchContainer<?, ?> batchContainer,
            PipelineType pipelineType,
            RenderFlowType flowType,
            KeyId stageId,
            RenderPostProcessors postProcessors,
            RenderContext context) {
        return createPackets((BatchContainer) batchContainer, pipelineType, flowType, stageId, postProcessors, context);
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public <G extends Graphics, I extends InstanceInfo<G>> Map<RenderSetting, List<RenderCommand>> createLegacyCommands(
            BatchContainer<G, I> batchContainer,
            RenderFlowType flowType,
            KeyId stageId,
            RenderPostProcessors postProcessors,
            RenderContext context) {

        Optional<RenderFlowStrategy<?, ?>> strategyOpt = flowRegistry.getStrategy(flowType);
        if (strategyOpt.isEmpty()) {
            return Collections.emptyMap();
        }

        RenderFlowStrategy<G, I> strategy = (RenderFlowStrategy<G, I>) strategyOpt.get();
        RenderFlowContext flowContext = new RenderFlowContext(vertexResourceManager, backendDataRegistry);

        TimerUtil.COMMAND_TIMER.start("create command " + flowType);
        Map<RenderSetting, List<RenderCommand>> commands = strategy.createRenderCommands(
                batchContainer, stageId, flowContext, postProcessors, context);
        TimerUtil.COMMAND_TIMER.end("create command " + flowType);

        return commands;
    }

    @Deprecated
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map<RenderSetting, List<RenderCommand>> createLegacyCommandsUnchecked(
            BatchContainer<?, ?> batchContainer,
            RenderFlowType flowType,
            KeyId stageId,
            RenderPostProcessors postProcessors,
            RenderContext context) {
        return createLegacyCommands((BatchContainer) batchContainer, flowType, stageId, postProcessors, context);
    }
}
