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
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public interface StageFlowScene<C extends RenderContext> {
    PipelineType pipelineType();

    void registerGraphicsInstance(
            Graphics graphics,
            RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier);

    void tick(C context);

    void asyncTick(C context);

    void swapData();

    void prepareForFrame();

    void cleanupDiscardedInstances();

    Map<PipelineStateKey, List<RenderPacket>> createRenderPackets(
            KeyId stageId,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context);

    @Deprecated
    default Map<RenderSetting, List<RenderCommand>> createLegacyCommands(
            KeyId stageId,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context) {
        return Collections.emptyMap();
    }

    void clear();

    void removeGraphicsInstance(Graphics graphics);

    int instanceCount();

    boolean hasInstances();

    default BatchContainer<?, ?> legacyBatchContainer() {
        return null;
    }
}
