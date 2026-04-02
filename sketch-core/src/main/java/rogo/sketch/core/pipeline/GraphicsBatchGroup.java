package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.container.ContainerDescriptor;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.flow.impl.ComputeBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.FunctionBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.RasterizationBatchContainer;
import rogo.sketch.core.pipeline.flow.v2.LegacyStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.RasterStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.StageFlowCompilerFacade;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.*;
import java.util.function.Supplier;

import static rogo.sketch.core.pipeline.PipelineType.*;

/**
 * Stage-local pipeline router.
 * <p>
 * Raster/translucent stages now compile through {@link RasterStageFlowScene}
 * and no longer use legacy {@link BatchContainer}-based packet compilation.
 * Legacy batch containers are retained only for compat compute/function flows
 * and migration seams that still need the old storage model.
 * </p>
 */
public class GraphicsBatchGroup<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final KeyId stageKeyId;
    private final Map<PipelineType, StageFlowScene<C>> flowScenes = new LinkedHashMap<>();

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
        PipelineDataStore dataStore = graphicsPipeline.getPipelineDataStore(pipelineType, FrameDataDomain.ASYNC_BUILD);
        if (pipelineType == RASTERIZATION || pipelineType == TRANSLUCENT) {
            flowScenes.put(pipelineType, new RasterStageFlowScene<>(
                    stageKeyId,
                    pipelineType,
                    resourceManager,
                    dataStore,
                    graphicsPipeline.renderTraceRecorder()));
            return;
        }

        BatchContainer<?, ?> container = createLegacyBatchContainer(pipelineType);
        GeometryBatchProcessor processor = new GeometryBatchProcessor(resourceManager, dataStore);
        flowScenes.put(pipelineType, new LegacyStageFlowScene<>(pipelineType, container, new StageFlowCompilerFacade(processor)));
    }

    /**
     * Create a legacy {@link BatchContainer} for non-raster compatibility
     * pipelines. Raster/translucent stages use {@link RasterStageFlowScene}.
     */
    private BatchContainer<?, ?> createLegacyBatchContainer(PipelineType pipelineType) {
        if (pipelineType == COMPUTE) {
            return new ComputeBatchContainer();
        }
        if (pipelineType == FUNCTION) {
            return new FunctionBatchContainer();
        }
        throw new IllegalArgumentException("Legacy batch containers are not used for pipeline type " + pipelineType);
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
        StageFlowScene<C> scene = flowScenes.get(pipelineType);
        if (scene == null) {
            throw new IllegalArgumentException("Pipeline type " + pipelineType + " does not contain any pipeline groups");
        }

        Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier =
                containerSupplier != null ? containerSupplier : resolveContainerSupplier(containerType);
        scene.registerGraphicsInstance(instance, renderParameter, containerType, supplier);
    }

    /**
     * Tick all instances across all pipeline types.
     *
     * @param context The render context
     */
    public void tick(C context) {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            scene.tick(context);
        }
    }

    public void asyncTick(C context) {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            scene.asyncTick(context);
        }
    }

    public void swapData() {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            scene.swapData();
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
     * Create render packets for a specific pipeline type using BatchContainer.
     *
     * @param pipelineType   Pipeline type to create commands for
     * @param context        Render context
     * @param postProcessors Post processors
     * @return Map of compiled pipeline states to packet lists
     */
    public Map<PipelineStateKey, List<RenderPacket>> createRenderPackets(PipelineType pipelineType, C context, RenderPostProcessors postProcessors) {
        try {
            StageFlowScene<C> scene = flowScenes.get(pipelineType);
            if (scene == null) {
                return Collections.emptyMap();
            }

            RenderFlowType flowType = pipelineType.getDefaultFlowType();

            Map<PipelineStateKey, List<RenderPacket>> packets = scene.createRenderPackets(
                    stageKeyId,
                    flowType,
                    postProcessors,
                    context);
            return packets != null ? packets : Collections.emptyMap();
        } catch (Exception e) {
            SketchDiagnostics.get().error("graphics-batch-group", "Failed to create render packets for stage " + stageKeyId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Create render packets for all pipeline types.
     *
     * @param context        Render context
     * @param postProcessors Post processors
     * @return Map of pipeline types to compiled packet groups
     */
    public Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> createAllRenderPackets(C context, RenderPostProcessors postProcessors) {
        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> allPackets = new LinkedHashMap<>();

        for (PipelineType pipelineType : flowScenes.keySet()) {
            Map<PipelineStateKey, List<RenderPacket>> packets = createRenderPackets(pipelineType, context, postProcessors);
            if (!packets.isEmpty()) {
                allPackets.put(pipelineType, packets);
            }
        }
        return allPackets;
    }

    public StageExecutionPlan createStageExecutionPlan(C context, RenderPostProcessors postProcessors) {
        return StageExecutionPlan.fromPackets(stageKeyId, createAllRenderPackets(context, postProcessors));
    }

    /**
     * Legacy compatibility path kept until backend migration finishes.
     */
    @Deprecated
    public Map<RenderSetting, List<rogo.sketch.core.command.RenderCommand>> createRenderCommands(PipelineType pipelineType, C context, RenderPostProcessors postProcessors) {
        try {
            StageFlowScene<C> scene = flowScenes.get(pipelineType);
            if (scene == null) {
                return Collections.emptyMap();
            }

            RenderFlowType flowType = pipelineType.getDefaultFlowType();

            return scene.createLegacyCommands(
                    stageKeyId,
                    flowType,
                    postProcessors,
                    context);
        } catch (Exception e) {
            SketchDiagnostics.get().error("graphics-batch-group", "Failed to create legacy render commands for stage " + stageKeyId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Clean up discarded instances from all batches across all pipeline types.
     * Also marks discarded instances for batch removal.
     */
    public void cleanupDiscardedInstances() {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            scene.cleanupDiscardedInstances();
        }
    }

    /**
     * Prepare all batches for a new frame.
     * Clears dirty instance caches and prepares for visibility updates.
     */
    public void prepareForFrame() {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            scene.prepareForFrame();
        }
    }

    /**
     * Get the BatchContainer for a specific pipeline type.
     *
     * @param pipelineType The pipeline type
     * @return The BatchContainer, or null if not found
     */
    @Deprecated(forRemoval = false)
    public BatchContainer<?, ?> getBatchContainer(PipelineType pipelineType) {
        StageFlowScene<C> scene = flowScenes.get(pipelineType);
        return scene != null ? scene.legacyBatchContainer() : null;
    }


    /**
     * Clear all instance groups across all pipeline types.
     */
    public void clear() {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            scene.clear();
        }
    }

    public void removeGraphicsInstance(Graphics graphics) {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            scene.removeGraphicsInstance(graphics);
        }
    }

    /**
     * Get the total number of instances across all pipeline types and groups.
     *
     * @return Total instance count
     */
    public int getTotalInstanceCount() {
        int total = 0;
        for (StageFlowScene<C> scene : flowScenes.values()) {
            total += scene.instanceCount();
        }
        return total;
    }

    /**
     * Check if this group has any instances across all pipeline types.
     *
     * @return true if there are instances
     */
    public boolean hasInstances() {
        for (StageFlowScene<C> scene : flowScenes.values()) {
            if (scene.hasInstances()) {
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
