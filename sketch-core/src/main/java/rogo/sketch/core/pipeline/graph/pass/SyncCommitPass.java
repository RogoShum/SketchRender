package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.RenderCommandQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.BuildResult;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

import java.util.List;
import java.util.Map;

/**
 * Synchronous pass that consumes the previous frame's async-built {@link BuildResult}.
 * <ol>
 *   <li>Consume BuildResult from the kernel resource bus</li>
 *   <li>If uploads were NOT completed on the worker, execute post-processors on main thread</li>
 *   <li>Materialize any new VAOs queued during async build</li>
 *   <li>Swap FrameDataStores (publish async-written data to sync/read side)</li>
 *   <li>Write render commands to RenderCommandQueue</li>
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
        GraphicsPipeline<C> pipeline = ctx.pipeline();

        BuildResult buildResult = ctx.kernel().consumeBuildResult();
        if (buildResult == null) return;

        Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> commands = buildResult.commands();
        RenderPostProcessors postProcessors = buildResult.postProcessors();

        // If uploads were NOT done on worker, execute post-processors on main thread now
        if (!buildResult.uploadsCompleted() && postProcessors != null) {
            postProcessors.executeAll();
        }

        // Materialize any VAOs that were queued during async build
        pipeline.materializePendingVertexResources();

        // Swap double-buffered data stores
        pipeline.swapFrameDataStores();

        for (PipelineType pipelineType : ctx.pipeline().getPipelineTypes()) {
            PipelineDataStore pipelineDataStore = ctx.pipeline().getPipelineDataStore(pipelineType);
            IndirectBufferData indirectBufferData = pipelineDataStore.get(IndirectBufferData.KEY);
            indirectBufferData.materializePending();
        }

        // Commit commands to render command queue
        RenderCommandQueue<C> queue = pipeline.getRenderCommandQueue();
        queue.clear();
        if (commands != null) {
            for (Map.Entry<PipelineType, Map<RenderSetting, List<RenderCommand>>> entry : commands.entrySet()) {
                queue.addCommands(entry.getKey(), entry.getValue());
            }
        }
    }
}