package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.container.ContainerDescriptor;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.flow.v2.ComputeStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.FunctionStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.RasterStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.function.Supplier;

import static rogo.sketch.core.pipeline.PipelineType.*;

/**
 * Stage-local pipeline router.
 * <p>
 * All formal pipeline types now compile directly through V2 stage scenes.
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
        if (pipelineType == RASTERIZATION || pipelineType == TRANSLUCENT) {
            flowScenes.put(pipelineType, new RasterStageFlowScene<>(
                    stageKeyId,
                    pipelineType,
                    graphicsPipeline.getGeometryResourceCoordinator(pipelineType),
                    () -> graphicsPipeline.getPipelineDataStore(pipelineType, FrameDataDomain.ASYNC_BUILD),
                    graphicsPipeline.renderTraceRecorder()));
            return;
        }
        if (pipelineType == COMPUTE) {
            flowScenes.put(pipelineType, new ComputeStageFlowScene<>(pipelineType));
            return;
        }
        if (pipelineType == FUNCTION) {
            flowScenes.put(pipelineType, new FunctionStageFlowScene<>(pipelineType));
            return;
        }
        throw new IllegalArgumentException("Unsupported pipeline type " + pipelineType + " for stage " + stageKeyId);
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

        Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier;
        if (pipelineType == RASTERIZATION || pipelineType == TRANSLUCENT) {
            supplier = null;
            if (containerSupplier != null) {
                SketchDiagnostics.get().warn(
                        "graphics-batch-group",
                        "Ignoring custom GraphicsContainer supplier for raster/translucent instance "
                                + (instance != null ? instance.getIdentifier() : KeyId.of("sketch:null_graphics"))
                                + " in stage " + stageKeyId
                                + ". Built-in VisibilityIndex implementations are now the only formal main path.");
            }
        } else {
            supplier = containerSupplier != null ? containerSupplier : resolveContainerSupplier(containerType);
        }
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
     * Create render packets for a specific pipeline type through the formal v2 stage flow scene.
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

