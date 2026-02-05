package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.impl.ComputeBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.ContainerListener;
import rogo.sketch.core.pipeline.flow.impl.FunctionBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.RasterizationBatchContainer;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static rogo.sketch.core.pipeline.PipelineType.*;

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

    // BatchContainers for each pipeline type
    private final Map<PipelineType, BatchContainer<?, ?>> batchContainers = new LinkedHashMap<>();

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

        // Create appropriate BatchContainer for this pipeline type
        BatchContainer<?, ?> container = createBatchContainer(pipelineType);
        if (container != null) {
            batchContainers.put(pipelineType, container);
        }
    }

    /**
     * Create the appropriate BatchContainer for a pipeline type.
     */
    private BatchContainer<?, ?> createBatchContainer(PipelineType pipelineType) {
        if (pipelineType == RASTERIZATION || pipelineType == TRANSLUCENT) {
            return new RasterizationBatchContainer();
        } else if (pipelineType == COMPUTE) {
            return new ComputeBatchContainer();
        } else {
            return new FunctionBatchContainer();
        }
    }

    public void addGraphInstance(Graphics instance, RenderParameter renderParameter, PipelineType pipelineType) {
        addGraphInstance(instance, renderParameter, pipelineType, GraphicsBatch.DEFAULT_CONTAINER);
    }

    public void addGraphInstance(Graphics instance, RenderParameter renderParameter, PipelineType pipelineType, KeyId containerType) {
        Map<RenderParameter, GraphicsBatch<C>> groups = pipelineGroups.get(pipelineType);
        if (groups == null) {
            throw new IllegalArgumentException("Pipeline type " + pipelineType + " does not contain any pipeline groups");
        }

        GraphicsBatch<C> batch = groups.computeIfAbsent(renderParameter, s -> {
            GraphicsBatch<C> newBatch = new GraphicsBatch<>();
            // Register BatchContainer as listener for all containers in this batch
            BatchContainer<?, ?> batchContainer = batchContainers.get(pipelineType);
            if (batchContainer != null) {
                newBatch.registerBatchContainerListener(batchContainer);
            }
            // Register global container listeners (e.g., TransformManager)
            for (ContainerListener listener : graphicsPipeline.getGlobalContainerListeners()) {
                newBatch.registerContainerListener(listener);
            }
            return newBatch;
        });
        batch.addGraphInstance(instance, containerType, renderParameter);
    }

    /**
     * Tick all instances across all pipeline types.
     *
     * @param context The render context
     */
    public void tick(C context) {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                batch.tick(context);
            }
        }
    }

    public void asyncTick(C context) {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                batch.asyncTick(context);
            }
        }
    }

    public void swapData() {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                batch.swapData();
            }
        }
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
     * Create render commands for a specific pipeline type using BatchContainer.
     *
     * @param pipelineType   Pipeline type to create commands for
     * @param context        Render context
     * @param postProcessors Post processors
     * @return Map of render settings to command lists
     */
    @SuppressWarnings("unchecked")
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(PipelineType pipelineType, C context, RenderPostProcessors postProcessors) {
        try {
            BatchContainer<?, ?> batchContainer = batchContainers.get(pipelineType);
            if (batchContainer == null || batchContainer.getActiveBatches().isEmpty()) {
                return Collections.emptyMap();
            }

            GeometryBatchProcessor processor = batchProcessors.get(pipelineType);
            if (processor == null) {
                return Collections.emptyMap();
            }

            // Get the flow type for this pipeline type
            RenderFlowType flowType = pipelineType.getDefaultFlowType();

            return processor.createCommands(
                    (BatchContainer) batchContainer,
                    flowType,
                    stageKeyId,
                    postProcessors,
                    context);
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
     * Also marks discarded instances for batch removal.
     */
    public void cleanupDiscardedInstances() {
        for (Map<RenderParameter, GraphicsBatch<C>> groups : pipelineGroups.values()) {
            for (GraphicsBatch<C> batch : groups.values()) {
                batch.cleanupDiscardedInstances();
            }
        }
    }

    /**
     * Prepare all batches for a new frame.
     * Clears dirty instance caches and prepares for visibility updates.
     */
    public void prepareForFrame() {
        // Prepare all BatchContainers for new frame
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            container.prepareForFrame();
        }
    }

    /**
     * Get the BatchContainer for a specific pipeline type.
     *
     * @param pipelineType The pipeline type
     * @return The BatchContainer, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends InstanceInfo<Graphics>> BatchContainer<?, T> getBatchContainer(PipelineType pipelineType) {
        return (BatchContainer<?, T>) batchContainers.get(pipelineType);
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