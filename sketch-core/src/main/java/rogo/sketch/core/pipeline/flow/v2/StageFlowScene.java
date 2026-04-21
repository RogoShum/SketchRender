package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;

import java.util.List;
import java.util.Map;

public interface StageFlowScene<C extends RenderContext> {
    PipelineType pipelineType();

    void prepareForFrame(GraphicsWorld world, StageEntityView view, C context);

    void tick(GraphicsWorld world, StageEntityView view, C context);

    void asyncTick(GraphicsWorld world, StageEntityView view, C context);

    void swapData(GraphicsWorld world, StageEntityView view);

    void cleanupDiscardedEntities(GraphicsWorld world, GraphicsEntityAssembler assembler, StageEntityView view);

    Map<ExecutionKey, List<RenderPacket>> createRenderPackets(
            StageEntityView view,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context);

    void clear();
}

