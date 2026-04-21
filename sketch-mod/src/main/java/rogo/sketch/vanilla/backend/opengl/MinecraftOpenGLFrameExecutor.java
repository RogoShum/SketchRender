package rogo.sketch.vanilla.backend.opengl;

import rogo.sketch.backend.opengl.OpenGLStateAccess;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendStageScope;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.backend.opengl.state.snapshot.GLStateSnapshot;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.util.KeyId;

import java.util.List;

final class MinecraftOpenGLFrameExecutor implements BackendFrameExecutor {
    private final GraphicsAPI api;
    private final BackendFrameExecutor delegate;
    private final OpenGLStateAccess stateAccess;

    MinecraftOpenGLFrameExecutor(GraphicsAPI api, BackendFrameExecutor delegate, OpenGLStateAccess stateAccess) {
        this.api = api;
        this.delegate = delegate;
        this.stateAccess = stateAccess;
    }

    @Override
    public <C extends RenderContext> BackendStageScope beginExecutionScope(
            GraphicsPipeline<C> pipeline,
            RenderPacketQueue<C> queue,
            List<KeyId> stageIds,
            SnapshotScope snapshotScope,
            C context) {
        if (snapshotScope == null || snapshotScope.isEmpty()) {
            return BackendStageScope.NO_OP;
        }
        GLStateSnapshot snapshot = api.snapshot(snapshotScope);
        return new MinecraftOpenGLStageScope(snapshot, stateAccess, api);
    }

    @Override
    public <C extends RenderContext> void executePacketGroup(
            GraphicsPipeline<C> pipeline,
            ExecutionKey stateKey,
            List<RenderPacket> packets,
            RenderStateManager manager,
            C context) {
        delegate.executePacketGroup(pipeline, stateKey, packets, manager, context);
    }

    @Override
    public <C extends RenderContext> void executeImmediate(
            GraphicsPipeline<C> pipeline,
            RenderPacket packet,
            RenderStateManager manager,
            C context) {
        delegate.executeImmediate(pipeline, packet, manager, context);
    }
}
