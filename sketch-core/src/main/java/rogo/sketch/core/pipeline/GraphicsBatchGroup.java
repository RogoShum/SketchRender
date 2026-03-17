package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.container.ContainerDescriptor;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.flow.impl.ComputeBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.FunctionBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.RasterizationBatchContainer;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.*;
import java.util.function.Supplier;

import static rogo.sketch.core.pipeline.PipelineType.*;

/**
 * Stage-local container that routes graphics instances to per-pipeline
 * {@link BatchContainer} implementations.
 * <p>
 * The legacy per-RenderParameter {@code GraphicsBatch} layer has been removed.
 * Batch organization, visibility preparation and dirty reconciliation are now
 * handled by each pipeline's merged {@link BatchContainer}.
 * </p>
 */
public class GraphicsBatchGroup<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final KeyId stageKeyId;
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
        VertexResourceManager resourceManager = graphicsPipeline.getVertexResourceManager(pipelineType);
        PipelineDataStore dataStore = graphicsPipeline.getPipelineDataStore(pipelineType);
        batchProcessors.put(pipelineType, new GeometryBatchProcessor(resourceManager, dataStore));

        // Create appropriate BatchContainer for this pipeline type
        BatchContainer<?, ?> container = createBatchContainer(pipelineType);
        batchContainers.put(pipelineType, container);
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
        addGraphInstance(instance, renderParameter, pipelineType, DefaultBatchContainers.DEFAULT);
    }

    public void addGraphInstance(Graphics instance, RenderParameter renderParameter, PipelineType pipelineType, KeyId containerType) {
        addGraphInstance(instance, renderParameter, pipelineType, containerType, null);
    }

    public void addGraphInstance(
            Graphics instance,
            RenderParameter renderParameter,
            PipelineType pipelineType,
            KeyId containerType,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> containerSupplier) {
        BatchContainer<?, ?> container = batchContainers.get(pipelineType);
        if (container == null) {
            throw new IllegalArgumentException("Pipeline type " + pipelineType + " does not contain any pipeline groups");
        }

        Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier =
                containerSupplier != null ? containerSupplier : resolveContainerSupplier(containerType);
        container.addGraphicsInstance(instance, renderParameter, containerType, supplier);
    }

    /**
     * Tick all instances across all pipeline types.
     *
     * @param context The render context
     */
    public void tick(C context) {
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            container.tick(context);
        }
    }

    public void asyncTick(C context) {
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            container.asyncTick(context);
        }
    }

    public void swapData() {
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            container.swapData();
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
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(PipelineType pipelineType, C context, RenderPostProcessors postProcessors) {
        try {
            BatchContainer<?, ?> batchContainer = batchContainers.get(pipelineType);
            if (batchContainer == null) {
                return Collections.emptyMap();
            }
            batchContainer.prepareVisibility(context);
            if (batchContainer.getActiveBatches().isEmpty()) {
                return Collections.emptyMap();
            }

            GeometryBatchProcessor processor = batchProcessors.get(pipelineType);
            if (processor == null) {
                return Collections.emptyMap();
            }

            // Get the flow type for this pipeline type
            RenderFlowType flowType = pipelineType.getDefaultFlowType();

            return processor.createCommandsUnchecked(
                    batchContainer,
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
        for (PipelineType pipelineType : batchContainers.keySet()) {
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
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            for (GraphicsContainer<? extends RenderContext> graphicsContainer : container.getActiveGraphicsContainers().values()) {
                List<Graphics> all = new ArrayList<>(graphicsContainer.getAllInstances());
                for (Graphics graphics : all) {
                    if (graphics.shouldDiscard()) {
                        container.removeGraphicsInstance(graphics);
                    }
                }
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
    public BatchContainer<?, ?> getBatchContainer(PipelineType pipelineType) {
        return batchContainers.get(pipelineType);
    }


    /**
     * Clear all instance groups across all pipeline types.
     */
    public void clear() {
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            container.clear();
        }
    }

    /**
     * Get the total number of instances across all pipeline types and groups.
     *
     * @return Total instance count
     */
    public int getTotalInstanceCount() {
        int total = 0;
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            for (rogo.sketch.core.pipeline.flow.RenderBatch<?> batch : container.getActiveBatches()) {
                total += batch.getInstanceCount();
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
        for (BatchContainer<?, ?> container : batchContainers.values()) {
            if (!container.getActiveBatches().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Supplier<? extends GraphicsContainer<? extends RenderContext>> resolveContainerSupplier(KeyId containerType) {
        ContainerDescriptor<RenderContext> descriptor = DefaultBatchContainers.find(containerType).orElse(null);
        return descriptor != null ? descriptor.supplier() : null;
    }
}