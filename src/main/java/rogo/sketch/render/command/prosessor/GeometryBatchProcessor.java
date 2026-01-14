package rogo.sketch.render.command.prosessor;

import rogo.sketch.api.graphics.*;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.flow.RenderFlowContext;
import rogo.sketch.render.pipeline.flow.RenderFlowRegistry;
import rogo.sketch.render.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.render.pipeline.flow.RenderFlowType;
import rogo.sketch.render.pipeline.information.InstanceInfo;
import rogo.sketch.render.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.render.pipeline.RenderParameter;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.Identifier;

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
    private final Map<RenderParameter, IndirectCommandBuffer> indirectBuffers;
    private final VertexResourceManager resourceManager = VertexResourceManager.getInstance();
    private final RenderFlowRegistry flowRegistry = RenderFlowRegistry.getInstance();

    public GeometryBatchProcessor(Map<RenderParameter, IndirectCommandBuffer> indirectBuffers) {
        this.indirectBuffers = indirectBuffers;
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
    public <C extends RenderContext> List<RenderCommand> createAllCommands(
            Map<RenderSetting, Collection<Graphics>> instanceGroups,
            Identifier stageId,
            C context) {

        // 1. Collect instance info using flow strategies
        Map<RenderFlowType, List<InstanceInfo>> infosByFlowType = new HashMap<>();

        for (Map.Entry<RenderSetting, Collection<Graphics>> entry : instanceGroups.entrySet()) {
            RenderSetting setting = entry.getKey();
            Collection<Graphics> instances = entry.getValue();
            RenderFlowType flowType = setting.renderParameter().getFlowType();

            Optional<RenderFlowStrategy> strategyOpt = flowRegistry.getStrategy(flowType);
            if (strategyOpt.isEmpty()) {
                continue; // No strategy for this flow type
            }

            RenderFlowStrategy strategy = strategyOpt.get();

            for (Graphics instance : instances) {
                InstanceInfo info = strategy.collectInstanceInfo(instance, setting, context);
                if (info != null) {
                    infosByFlowType.computeIfAbsent(flowType, k -> new ArrayList<>()).add(info);
                }
            }
        }

        // 2. Create render commands for each flow type
        List<RenderCommand> allCommands = new ArrayList<>();
        RenderFlowContext flowContext = new RenderFlowContext(resourceManager, indirectBuffers);

        for (Map.Entry<RenderFlowType, List<InstanceInfo>> entry : infosByFlowType.entrySet()) {
            RenderFlowType flowType = entry.getKey();
            List<InstanceInfo> infos = entry.getValue();

            Optional<RenderFlowStrategy> strategyOpt = flowRegistry.getStrategy(flowType);
            if (strategyOpt.isEmpty())
                continue;

            RenderFlowStrategy strategy = strategyOpt.get();
            allCommands.addAll(strategy.createRenderCommands(infos, stageId, flowContext));
        }

        return allCommands;
    }

    // ===== Getters for context (kept for legacy or compatibility if needed) =====

    public VertexResourceManager getResourceManager() {
        return resourceManager;
    }

    public Map<RenderParameter, IndirectCommandBuffer> getIndirectBuffers() {
        return indirectBuffers;
    }
}