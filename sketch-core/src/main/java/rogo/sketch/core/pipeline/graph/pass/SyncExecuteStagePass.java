package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync pass that executes all render packets by iterating stages.
 * <p>
 * Replaces the old manual {@code renderStage()} calls with a single
 * pass that walks all stages and dispatches via {@link RenderPacketQueue}.
 * </p>
 */
public class SyncExecuteStagePass<C extends RenderContext> implements PipelinePass<C> {

    public static final String NAME = "sync_execute_stages";

    @Override
    public String name() { return NAME; }

    @Override
    public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

    @Override
    @SyncOnly("GL rendering commands")
    public void execute(FrameContext<C> ctx) {
        GraphicsPipeline<C> pipeline = ctx.pipeline();
        C renderContext = ctx.renderContext();
        RenderStateManager stateManager = pipeline.renderStateManager();
        RenderPacketQueue<C> queue = pipeline.getRenderPacketQueue();

        List<KeyId> stageIds = new ArrayList<>();
        for (GraphicsStage stage : pipeline.getOrderedStages()) {
            stageIds.add(stage.getIdentifier());
        }
        queue.executeStageRange(stageIds, stateManager, renderContext);

        // Flush any remaining translucent commands
        queue.flushRemainingTranslucentPackets(stateManager, renderContext);
    }
}

