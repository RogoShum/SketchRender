package rogo.sketch.render.pipeline;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.render.pipeline.async.AsyncRenderManager;
import rogo.sketch.render.pipeline.flow.RenderPostProcessors;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.util.KeyId;

import java.util.*;

/**
 * Container for graphics instances grouped by render settings.
 * <p>
 * This class is now a pure data container responsible for:
 * <ul>
 * <li>Storing and organizing graphics instances by render setting</li>
 * <li>Ticking instances each frame</li>
 * <li>Providing instance groups to the batch processor for command
 * creation</li>
 * <li>Managing instance lifecycle (cleanup, clear)</li>
 * </ul>
 * </p>
 * <p>
 * Command creation logic has been moved to {@link GeometryBatchProcessor}.
 * </p>
 */
public class GraphicsBatchGroup<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final KeyId stageKeyId;
    private final Map<RenderSetting, GraphicsBatch<C>> groups = new LinkedHashMap<>();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();

    private final GeometryBatchProcessor batchProcessor;

    public GraphicsBatchGroup(GraphicsPipeline<C> graphicsPipeline, KeyId stageKeyId) {
        this.graphicsPipeline = graphicsPipeline;
        this.stageKeyId = stageKeyId;
        // Indirect buffers managed here but used by processor
        this.batchProcessor = new GeometryBatchProcessor(graphicsPipeline.indirectBuffers(), graphicsPipeline.instancedOffsets());
    }

    /**
     * Add a graphics instance with its render setting.
     *
     * @param instance The graphics instance
     * @param setting  The render setting for this instance
     */
    public void addGraphInstance(Graphics instance, RenderSetting setting) {
        GraphicsBatch<C> group = groups.computeIfAbsent(setting, s -> new GraphicsBatch<>());
        group.addGraphInstance(instance);
    }

    /**
     * Tick all instances in this group.
     *
     * @param context The render context
     */
    public void tick(C context) {
        Collection<Graphics> allInstances = groups.values().stream()
                .flatMap(graphicsBatch -> graphicsBatch.getAllInstances().stream())
                .toList();

        if (asyncManager.shouldUseAsync(allInstances.size())) {
            asyncManager.tickInstancesAsync(allInstances, context).join();
        } else {
            tickSync(context);
        }
    }

    private void tickSync(C context) {
        groups.values().forEach(group -> group.tick(context));
    }

    /**
     * Get the instance groups as a map of render settings to instances.
     * This is the primary data accessor for the batch processor.
     *
     * @return Map of render settings to instance collections
     */
    public Map<RenderSetting, Collection<Graphics>> getInstanceGroups() {
        Map<RenderSetting, Collection<Graphics>> result = new LinkedHashMap<>();
        for (Map.Entry<RenderSetting, GraphicsBatch<C>> entry : groups.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAllInstances());
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

    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(
            C context,
            RenderPostProcessors postProcessors) {
        try {
            Map<RenderSetting, Collection<Graphics>> instanceGroups = getInstanceGroups();
            if (instanceGroups.isEmpty()) {
                return Collections.emptyMap();
            }

            return batchProcessor.createAllCommands(instanceGroups, stageKeyId, context, postProcessors);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    /**
     * Clean up discarded instances from all batches.
     */
    public void cleanupDiscardedInstances() {
        for (GraphicsBatch<C> graphicsBatch : groups.values()) {
            graphicsBatch.cleanupDiscardedInstances(poolManager);
        }
    }

    /**
     * Clear all instance groups.
     */
    public void clear() {
        groups.clear();
    }

    /**
     * Get the total number of instances across all groups.
     *
     * @return Total instance count
     */
    public int getTotalInstanceCount() {
        return groups.values().stream()
                .mapToInt(batch -> batch.getAllInstances().size())
                .sum();
    }

    /**
     * Check if this group has any instances.
     *
     * @return true if there are instances
     */
    public boolean hasInstances() {
        return groups.values().stream()
                .anyMatch(batch -> !batch.getAllInstances().isEmpty());
    }
}