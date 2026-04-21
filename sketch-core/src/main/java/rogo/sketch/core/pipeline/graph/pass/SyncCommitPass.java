package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

/**
 * Synchronous pass that consumes the previous frame's async-built {@link BuildResult}.
 * <ol>
 *   <li>Consume BuildResult from the kernel resource bus</li>
 *   <li>If uploads were NOT completed on the worker, execute post-processors on main thread</li>
 *   <li>Materialize any new VAOs queued during async build</li>
 *   <li>Swap FrameDataStores (publish async-written data to sync/read side)</li>
 *   <li>Write render packets to RenderPacketQueue</li>
 * </ol>
 */
public class SyncCommitPass<C extends RenderContext> implements PipelinePass<C> {

    public static final String NAME = "sync_commit";

    @Override
    public String name() { return NAME; }

    @Override
    public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

    @Override
    public void execute(FrameContext<C> ctx) {
        ctx.kernel().commitPipeline().execute(ctx);
    }
}

