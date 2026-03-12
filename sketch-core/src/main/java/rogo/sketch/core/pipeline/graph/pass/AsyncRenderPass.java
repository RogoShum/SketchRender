package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.driver.GLRuntimeFlags;
import rogo.sketch.core.pipeline.*;
import rogo.sketch.core.pipeline.flow.RenderFlowRegistry;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.BuildResult;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Async pass running on the render worker thread (may have shared GL context).
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>If {@code allowShaderWorker()}: compile needed shader variants directly (thread has GL context)</li>
 *   <li>Build render commands from all stages</li>
 *   <li>If {@code allowUploadWorker()}: execute post-processor uploads (VBO upload on worker)</li>
 *   <li>Publish {@link BuildResult} to cross-frame slot</li>
 * </ol>
 * When uploads are NOT allowed on the worker, the post-processors are still created
 * and included in the BuildResult so the main thread can execute them in SyncCommitPass.
 */
public class AsyncRenderPass<C extends RenderContext> implements PipelinePass<C> {

    public static final String NAME = "async_render";

    @Override
    public String name() { return NAME; }

    @Override
    public ThreadDomain threadDomain() { return ThreadDomain.ASYNC; }

    @Override
    public void execute(FrameContext<C> ctx) {
        GraphicsPipeline<C> pipeline = ctx.pipeline();
        C renderContext = ctx.renderContext();

        // Create post-processors
        RenderPostProcessors postProcessors = new RenderPostProcessors();
        for (RenderFlowStrategy strategy : RenderFlowRegistry.getInstance().getAllStrategies()) {
            RenderPostProcessor processor = strategy.createPostProcessor();
            if (processor != null) {
                postProcessors.register(strategy.getFlowType(), processor);
            }
        }

        // Build commands from all stages (shader compile happens on-demand if worker has GL)
        Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> allCommands = new HashMap<>();
        for (GraphicsStage stage : pipeline.getOrderedStages()) {
            GraphicsBatchGroup<C> batchGroup = pipeline.getBatchGroup(stage);
            if (batchGroup != null) {
                Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> stageCommands =
                        batchGroup.createAllRenderCommands(renderContext, postProcessors);

                for (Map.Entry<PipelineType, Map<RenderSetting, List<RenderCommand>>> entry : stageCommands.entrySet()) {
                    allCommands.computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                            .putAll(entry.getValue());
                }
            }
        }

        // Execute uploads on worker if allowed
        boolean uploadsCompleted = false;
        if (GLRuntimeFlags.allowUploadWorker()) {
            postProcessors.executeAll();
            uploadsCompleted = true;
        }

        // Publish result
        ctx.kernel().publishBuildResult(new BuildResult(
                ctx.frameNumber(),
                allCommands,
                postProcessors,
                uploadsCompleted
        ));
    }
}

