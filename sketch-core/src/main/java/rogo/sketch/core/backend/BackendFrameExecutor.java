package rogo.sketch.core.backend;

import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public interface BackendFrameExecutor {
    <C extends RenderContext> BackendStageScope beginExecutionScope(
            GraphicsPipeline<C> pipeline,
            RenderPacketQueue<C> queue,
            List<KeyId> stageIds,
            SnapshotScope snapshotScope,
            C context);

    default <C extends RenderContext> BackendStageScope beginStage(
            GraphicsPipeline<C> pipeline,
            RenderPacketQueue<C> queue,
            KeyId stageId,
            C context) {
        return beginExecutionScope(
                pipeline,
                queue,
                List.of(stageId),
                queue.snapshotScopeForStages(List.of(stageId)),
                context);
    }

    <C extends RenderContext> void executePacketGroup(
            GraphicsPipeline<C> pipeline,
            PipelineStateKey stateKey,
            List<RenderPacket> packets,
            RenderStateManager manager,
            C context);

    <C extends RenderContext> void executeImmediate(
            GraphicsPipeline<C> pipeline,
            RenderPacket packet,
            RenderStateManager manager,
            C context);
}
