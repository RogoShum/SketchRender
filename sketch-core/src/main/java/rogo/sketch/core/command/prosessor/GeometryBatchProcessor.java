package rogo.sketch.core.command.prosessor;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.*;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.vertex.VertexResourceManager;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.TimerUtil;

import java.util.*;

/**
 * Core processor for converting GraphicsInstances into RenderCommands.
 * <p>
 * This processor now acts as a facade for the flow-based architecture:
 * <ul>
 * <li>Collects instance information via {@link RenderFlowStrategy}</li>
 * <li>Delegates command creation to appropriate {@link RenderFlowStrategy}</li>
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
     * Create all render commands from a map of render settings to graphics
     * instances.
     * This is the main entry point that uses the flow-based architecture.
     *
     * @param instanceGroups Map of render settings to their associated instances
     * @param stageId        The stage identifier
     * @param context        The render context
     * @param <C>            The render context type
     * @return List of render commands
     */
    public <C extends RenderContext> Map<RenderSetting, List<RenderCommand>> createAllCommands(Map<RenderParameter, Collection<Graphics>> instanceGroups, KeyId stageId, C context, RenderPostProcessors postProcessors) {
        // 1. Collect instance info using flow strategies
        Map<RenderFlowType, List<InstanceInfo>> infosByFlowType = new HashMap<>();

        for (Map.Entry<RenderParameter, Collection<Graphics>> entry : instanceGroups.entrySet()) {
            RenderParameter renderParameter = entry.getKey();
            Collection<Graphics> instances = entry.getValue();
            RenderFlowType flowType = renderParameter.getFlowType();

            Optional<RenderFlowStrategy> strategyOpt = flowRegistry.getStrategy(flowType);
            if (strategyOpt.isEmpty()) {
                continue; // No strategy for this flow type
            }

            RenderFlowStrategy strategy = strategyOpt.get();

            if (strategy.supportsParallel()) {
                List<InstanceInfo> collected = instances.parallelStream()
                        .map(instance -> strategy.collectInstanceInfo(instance, renderParameter, context))
                        .filter(Objects::nonNull)
                        .toList();
                infosByFlowType.computeIfAbsent(flowType, k -> new ArrayList<>()).addAll(collected);
            } else {
                for (Graphics instance : instances) {
                    InstanceInfo info = strategy.collectInstanceInfo(instance, renderParameter, context);
                    if (info != null) {
                        infosByFlowType.computeIfAbsent(flowType, k -> new ArrayList<>()).add(info);
                    }
                }
            }
        }

        // 2. Create render commands for each flow type
        Map<RenderSetting, List<RenderCommand>> allCommands = new LinkedHashMap<>();
        RenderFlowContext flowContext = new RenderFlowContext(vertexResourceManager, backendDataRegistry);

        TimerUtil.COMMAND_TIMER.start("create command");
        for (Map.Entry<RenderFlowType, List<InstanceInfo>> entry : infosByFlowType.entrySet()) {
            RenderFlowType flowType = entry.getKey();
            List<InstanceInfo> infos = entry.getValue();

            Optional<RenderFlowStrategy> strategyOpt = flowRegistry.getStrategy(flowType);
            if (strategyOpt.isEmpty())
                continue;

            RenderFlowStrategy strategy = strategyOpt.get();
            Map<RenderSetting, List<RenderCommand>> strategyCommands = strategy.createRenderCommands(infos, stageId, flowContext, postProcessors);

            // Merge into allCommands
            for (Map.Entry<RenderSetting, List<RenderCommand>> cmdEntry : strategyCommands.entrySet()) {
                allCommands.computeIfAbsent(cmdEntry.getKey(), k -> new ArrayList<>()).addAll(cmdEntry.getValue());
            }
        }
        TimerUtil.COMMAND_TIMER.end("create command");

        return allCommands;
    }
}