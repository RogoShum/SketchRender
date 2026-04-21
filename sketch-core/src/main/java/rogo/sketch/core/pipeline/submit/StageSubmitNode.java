package rogo.sketch.core.pipeline.submit;

import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.Objects;

/**
 * Stage-local submit node used to host upload/dispatch/draw/barrier work.
 */
public final class StageSubmitNode {
    @FunctionalInterface
    public interface StageSubmitExecutor {
        void execute(
                GraphicsPipeline<?> pipeline,
                RenderPacketQueue<?> queue,
                RenderStateManager manager,
                RenderContext context);
    }

    public enum NodeType {
        UPLOAD,
        DISPATCH,
        DRAW,
        BARRIER
    }

    private final String ownerId;
    private final KeyId nodeId;
    private final KeyId stageId;
    private final StageWindow window;
    private final NodeType nodeType;
    private final List<KeyId> reads;
    private final List<KeyId> writes;
    private final int sortHint;
    private final StageSubmitExecutor executor;
    private final boolean synthetic;

    public StageSubmitNode(
            String ownerId,
            KeyId nodeId,
            KeyId stageId,
            StageWindow window,
            NodeType nodeType,
            List<KeyId> reads,
            List<KeyId> writes,
            int sortHint,
            StageSubmitExecutor executor,
            boolean synthetic) {
        this.ownerId = ownerId != null ? ownerId : "unknown";
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.stageId = Objects.requireNonNull(stageId, "stageId");
        this.window = Objects.requireNonNull(window, "window");
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType");
        this.reads = reads != null ? List.copyOf(reads) : List.of();
        this.writes = writes != null ? List.copyOf(writes) : List.of();
        this.sortHint = sortHint;
        this.executor = executor != null ? executor : (pipeline, queue, manager, context) -> {};
        this.synthetic = synthetic;
    }

    public static StageSubmitNode synthetic(
            String ownerId,
            KeyId nodeId,
            KeyId stageId,
            StageWindow window,
            NodeType nodeType,
            int sortHint) {
        return new StageSubmitNode(
                ownerId,
                nodeId,
                stageId,
                window,
                nodeType,
                List.of(),
                List.of(),
                sortHint,
                (pipeline, queue, manager, context) -> {},
                true);
    }

    public String ownerId() {
        return ownerId;
    }

    public KeyId nodeId() {
        return nodeId;
    }

    public KeyId stageId() {
        return stageId;
    }

    public StageWindow window() {
        return window;
    }

    public NodeType nodeType() {
        return nodeType;
    }

    public List<KeyId> reads() {
        return reads;
    }

    public List<KeyId> writes() {
        return writes;
    }

    public int sortHint() {
        return sortHint;
    }

    public boolean synthetic() {
        return synthetic;
    }

    public void execute(
            GraphicsPipeline<?> pipeline,
            RenderPacketQueue<?> queue,
            RenderStateManager manager,
            RenderContext context) {
        executor.execute(pipeline, queue, manager, context);
    }
}
