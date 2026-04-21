package rogo.sketch.core.backend;

import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.submit.StageWindow;
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
            ExecutionKey stateKey,
            List<RenderPacket> packets,
            RenderStateManager manager,
            C context);

    default <C extends RenderContext> void executeStageWindow(
            GraphicsPipeline<C> pipeline,
            RenderPacketQueue<C> queue,
            GraphicsStage stage,
            KeyId stageId,
            StageWindow window,
            RenderStateManager manager,
            C context) {
        switch (window) {
            case PRE_STAGE_UPLOAD -> {
            }
            case PRE_STAGE_DISPATCH -> queue.executeStageDispatchWindow(stageId, manager, context);
            case DRAW -> queue.executeStageDrawWindow(stageId, stage, manager, context);
            case POST_STAGE -> {
            }
        }
    }

    <C extends RenderContext> void executeImmediate(
            GraphicsPipeline<C> pipeline,
            RenderPacket packet,
            RenderStateManager manager,
            C context);
}
