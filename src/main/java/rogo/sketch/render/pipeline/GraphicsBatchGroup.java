package rogo.sketch.render.pipeline;

import rogo.sketch.SketchRender;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.render.pipeline.async.AsyncRenderManager;
import rogo.sketch.render.pipeline.data.PipelineDataStore;
import rogo.sketch.render.pipeline.flow.RenderPostProcessors;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.util.KeyId;

import java.util.*;

/**
 * Container for graphics instances grouped by pipeline type and render
 * settings.
 * <p>
 * This class manages instances across multiple pipeline types (compute,
 * rasterization, translucent),
 * Each pipeline type has its own batch groups and batch processor with
 * dedicated resource managers.
 * </p>
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Storing and organizing graphics instances by pipeline type and render
 * setting</li>
 * <li>Ticking instances each frame across all pipeline types</li>
 * <li>Creating render commands for each pipeline type</li>
 * <li>Managing instance lifecycle (cleanup, clear)</li>
 * </ul>
 * </p>
 */
public class GraphicsBatchGroup<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final KeyId stageKeyId;
    private final Map<PipelineType, Map<RenderParameter, GraphicsBatch<C>>> pipelineGroups = new LinkedHashMap<>();
    private final Map<PipelineType, GeometryBatchProcessor> batchProcessors = new LinkedHashMap<>();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();

    public GraphicsBatchGroup(GraphicsPipeline<C> graphicsPipeline, KeyId stageKeyId) {
        this.graphicsPipeline = graphicsPipeline;
        this.stageKeyId = stageKeyId;

        // Initialize batch groups for default pipeline types
        for (PipelineType pipelineType : graphicsPipeline.getPipelineTypes()) {
            initializePipeline(pipelineType);
        }
    }

    private void initializePipeline(PipelineType pipelineType) {
        pipelineGroups.put(pipelineType, new LinkedHashMap<>());
        VertexResourceManager resourceManager = graphicsPipeline.getVertexResourceManager(pipelineType);
        PipelineDataStore dataStore = graphicsPipeline.getPipelineDataStore(pipelineType);
        batchProcessors.put(pipelineType, new GeometryBatchProcessor(resourceManager, dataStore));
    }

    public void addGraphInstance(Graphics instance, RenderParameter renderParameter, PipelineType pipelineType) {
        addGraphInstance(instance, renderParameter, pipelineType, GraphicsBatch.DEFAULT_CONTAINER);
    }

    public void addGraphInstance(Graphics instance, RenderParameter renderParameter, PipelineType pipelineType, KeyId containerType) {
        Map<RenderParameter, GraphicsBatch<C>> groups = pipelineGroups.get(pipelineType);
        if (groups == null) {
            throw new IllegalArgumentException("Pipeline type " + pipelineType + " does not contain any pipeline groups");
        }

        GraphicsBatch<C> batch = groups.computeIfAbsent(renderParameter, s -> new GraphicsBatch<>());
        batch.addGraphInstance(instance, containerType);
    }

    /**
     * Tick all instances across all pipeline types.
     *
     * @param context The render context
     */
    public void tick(C context) {
        // Collect all instances from all pipeline types
        Collection<Graphics> allInstances = new ArrayList<>();
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                allInstances.addAll(batch.getAllInstances());
            }
        }

        if (asyncManager.shouldUseAsync(allInstances.size())) {
            asyncManager.tickInstancesAsync(allInstances, context).join();
        } else {
            tickSync(context);
        }
    }

    private void tickSync(C context) {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                batch.tick(context);
            }
        }
    }

    /**
     * Get the instance groups for a specific pipeline type.
     *
     * @param pipelineType Pipeline type
     * @return Map of render parameters to instance collections
     */
    public Map<RenderParameter, Collection<Graphics>> getInstanceGroups(PipelineType pipelineType) {
        Map<RenderParameter, GraphicsBatch<C>> groups = pipelineGroups.get(pipelineType);
        if (groups == null) {
            return Collections.emptyMap();
        }

        Map<RenderParameter, Collection<Graphics>> result = new LinkedHashMap<>();
        for (Map.Entry<RenderParameter, GraphicsBatch<C>> entry : groups.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getVisibleInstances(graphicsPipeline.currentContext()));
        }
        return result;
    }

    /**
     * Get the stage identifier for this group.
     *
     * @return The stage identifier
     */
    public KeyId getStageIdentifier() {
        return stageKeyId;
    }

    /**
     * Create render commands for a specific pipeline type.
     *
     * @param pipelineType   Pipeline type to create commands for
     * @param context        Render context
     * @param postProcessors Post processors
     * @return Map of render settings to command lists
     */
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(PipelineType pipelineType, C context, RenderPostProcessors postProcessors) {
        try {
            Map<RenderParameter, Collection<Graphics>> instanceGroups = getInstanceGroups(pipelineType);
            if (instanceGroups.isEmpty()) {
                return Collections.emptyMap();
            }

            GeometryBatchProcessor processor = batchProcessors.get(pipelineType);
            if (processor == null) {
                return Collections.emptyMap();
            }

            return processor.createAllCommands(instanceGroups, stageKeyId, context, postProcessors);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    /**
     * Create render commands for all pipeline types.
     *
     * @param context        Render context
     * @param postProcessors Post processors
     * @return Map of pipeline types to render setting command maps
     */
    public Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> createAllRenderCommands(C context, RenderPostProcessors postProcessors) {
        Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> allCommands = new LinkedHashMap<>();

        //SketchRender.COMMAND_TIMER.start("compute command -> " + this.stageKeyId);
        for (PipelineType pipelineType : pipelineGroups.keySet()) {
            Map<RenderSetting, List<RenderCommand>> commands = createRenderCommands(pipelineType, context, postProcessors);
            if (!commands.isEmpty()) {
                allCommands.put(pipelineType, commands);
            }
        }
        //SketchRender.COMMAND_TIMER.end("compute command -> " + this.stageKeyId);
        return allCommands;
    }

    /**
     * Clean up discarded instances from all batches across all pipeline types.
     */
    public void cleanupDiscardedInstances() {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                batch.cleanupDiscardedInstances(poolManager);
            }
        }
    }

    /**
     * Clear all instance groups across all pipeline types.
     */
    public void clear() {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            groups.clear();
        }
    }

    /**
     * Get the total number of instances across all pipeline types and groups.
     *
     * @return Total instance count
     */
    public int getTotalInstanceCount() {
        int total = 0;
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                total += batch.getAllInstances().size();
            }
        }
        return total;
    }

    /**
     * Check if this group has any instances across all pipeline types.
     *
     * @return true if there are instances
     */
    public boolean hasInstances() {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                if (!batch.getAllInstances().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}