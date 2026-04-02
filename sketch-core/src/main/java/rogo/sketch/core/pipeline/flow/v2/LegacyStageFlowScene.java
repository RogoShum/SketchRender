package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Compatibility scene that keeps legacy {@link BatchContainer}-based flow
 * compilation alive for non-raster pipelines and migration seams.
 * <p>
 * Raster/translucent stages should use {@link RasterStageFlowScene} as the
 * formal V2 packet compilation path.
 * </p>
 */
public final class LegacyStageFlowScene<C extends RenderContext, G extends Graphics, I extends InstanceInfo<G>>
        implements StageFlowScene<C> {
    private final PipelineType pipelineType;
    private final BatchContainer<G, I> batchContainer;
    private final StageFlowCompilerFacade batchCompiler;

    public LegacyStageFlowScene(
            PipelineType pipelineType,
            BatchContainer<G, I> batchContainer,
            StageFlowCompilerFacade batchCompiler) {
        this.pipelineType = pipelineType;
        this.batchContainer = batchContainer;
        this.batchCompiler = batchCompiler;
    }

    @Override
    public PipelineType pipelineType() {
        return pipelineType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerGraphicsInstance(
            Graphics graphics,
            RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        if (graphics == null) {
            return;
        }
        batchContainer.addInstance((G) graphics, renderParameter, containerId, supplier);
    }

    @Override
    public void tick(C context) {
        batchContainer.tick(context);
    }

    @Override
    public void asyncTick(C context) {
        batchContainer.asyncTick(context);
    }

    @Override
    public void swapData() {
        batchContainer.swapData();
    }

    @Override
    public void prepareForFrame() {
        batchContainer.prepareForFrame();
    }

    @Override
    public void cleanupDiscardedInstances() {
        for (GraphicsContainer<? extends RenderContext> graphicsContainer : batchContainer.getActiveGraphicsContainers().values()) {
            List<Graphics> all = new ArrayList<>(graphicsContainer.getAllInstances());
            for (Graphics graphics : all) {
                if (graphics.shouldDiscard()) {
                    batchContainer.removeGraphicsInstance(graphics);
                }
            }
        }
    }

    @Override
    public Map<PipelineStateKey, List<RenderPacket>> createRenderPackets(
            KeyId stageId,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context) {
        batchContainer.prepareVisibility(context);
        if (batchContainer.getActiveBatches().isEmpty()) {
            return Collections.emptyMap();
        }
        return batchCompiler.compilePackets(batchContainer, pipelineType, flowType, stageId, postProcessors, context);
    }

    @Override
    @Deprecated
    public Map<RenderSetting, List<RenderCommand>> createLegacyCommands(
            KeyId stageId,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context) {
        batchContainer.prepareVisibility(context);
        if (batchContainer.getActiveBatches().isEmpty()) {
            return Collections.emptyMap();
        }
        return batchCompiler.compileLegacyCommands(batchContainer, flowType, stageId, postProcessors, context);
    }

    @Override
    public void clear() {
        batchContainer.clear();
    }

    @Override
    public void removeGraphicsInstance(Graphics graphics) {
        batchContainer.removeGraphicsInstance(graphics);
    }

    @Override
    public int instanceCount() {
        int total = 0;
        for (RenderBatch<I> batch : batchContainer.getActiveBatches()) {
            total += batch.getInstanceCount();
        }
        return total;
    }

    @Override
    public boolean hasInstances() {
        return !batchContainer.getActiveBatches().isEmpty();
    }

    @Override
    public BatchContainer<?, ?> legacyBatchContainer() {
        return batchContainer;
    }
}
