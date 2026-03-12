package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.command.RenderCommandQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;

/**
 * Sync pass that executes all render commands by iterating stages.
 * <p>
 * Replaces the old manual {@code renderStage()} calls with a single
 * pass that walks all stages and dispatches via {@link RenderCommandQueue}.
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
        RenderCommandQueue<C> queue = pipeline.getRenderCommandQueue();

        for (GraphicsStage stage : pipeline.getOrderedStages()) {
            queue.executeStage(stage.getIdentifier(), stateManager, renderContext);
        }

        // Flush any remaining translucent commands
        queue.flushRemainingTranslucentCommands(stateManager, renderContext);
    }
}

