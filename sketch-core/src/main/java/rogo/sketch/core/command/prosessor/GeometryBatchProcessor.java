package rogo.sketch.core.command.prosessor;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
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
 * Core processor for converting GraphicsInstances into RenderCommands.
 * <p>
 * This processor now acts as a facade for the flow-based architecture:
 * <ul>
 * <li>Delegates command creation to appropriate {@link RenderFlowStrategy}</li>
 * <li>Uses {@link BatchContainer} for pre-organized batches</li>
 * </ul>
 * </p>
 */
public class GeometryBatchProcessor {
    private final PipelineDataStore backendDataRegistry;
    private final VertexResourceManager vertexResourceManager;
    private final RenderFlowRegistry flowRegistry = RenderFlowRegistry.getInstance();

    public GeometryBatchProcessor(VertexResourceManager vertexResourceManager, PipelineDataStore backendDataRegistry) {
        this.backendDataRegistry = backendDataRegistry;
        this.vertexResourceManager = vertexResourceManager;
    }

    /**
     * Create all render commands using BatchContainer.
     * This is the new entry point using pre-organized batches.
     *
     * @param batchContainer The BatchContainer with pre-organized batches
     * @param flowType       The flow type for strategy selection
     * @param stageId        The stage identifier
     * @param postProcessors Post processors
     * @param <G>            Graphics type
     * @param <I>            InstanceInfo type
     * @return Map of render commands grouped by RenderSetting
     */
    @SuppressWarnings("unchecked")
    public <G extends Graphics, I extends InstanceInfo<G>> Map<RenderSetting, List<RenderCommand>> createCommands(
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
}