package rogo.sketch.core.backend;

import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public enum NoOpBackendRuntime implements BackendRuntime {
    INSTANCE;

    private static final BackendFrameExecutor EXECUTOR = new BackendFrameExecutor() {
        @Override
        public <C extends RenderContext> BackendStageScope beginExecutionScope(
                GraphicsPipeline<C> pipeline,
                RenderPacketQueue<C> queue,
                List<KeyId> stageIds,
                SnapshotScope snapshotScope,
                C context) {
            return BackendStageScope.NO_OP;
        }

        @Override
        public <C extends RenderContext> void executePacketGroup(
                GraphicsPipeline<C> pipeline,
                PipelineStateKey stateKey,
                List<RenderPacket> packets,
                RenderStateManager manager,
                C context) {
            throw new IllegalStateException("Graphics backend has not been bootstrapped");
        }

        @Override
        public <C extends RenderContext> void executeImmediate(
                GraphicsPipeline<C> pipeline,
                RenderPacket packet,
                RenderStateManager manager,
                C context) {
            throw new IllegalStateException("Graphics backend has not been bootstrapped");
        }
    };

    @Override
    public String backendName() {
        return "uninitialized";
    }

    @Override
    public BackendKind kind() {
        return BackendKind.UNINITIALIZED;
    }

    @Override
    public BackendCapabilities capabilities() {
        return BackendCapabilities.NONE;
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return EXECUTOR;
    }

    @Override
    public void assertMainThread(String caller) {
        throw new IllegalStateException("Graphics backend has not been bootstrapped: " + caller);
    }

    @Override
    public void assertRenderContext(String caller) {
        throw new IllegalStateException("Graphics backend has not been bootstrapped: " + caller);
    }
}
