package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.*;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.BuildResult;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Async pass running on the render worker thread (may have shared GL context).
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Build render packets from all stages</li>
 *   <li>If backend supports upload worker: execute post-processor uploads on worker</li>
 *   <li>Publish {@link BuildResult} to the kernel resource bus</li>
 * </ol>
 * When uploads are not allowed on the worker, the post-processors are still created
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
        pipeline.renderTraceRecorder().beginFrame(ctx.frameNumber());

        // Create post-processors
        RenderPostProcessors postProcessors = new RenderPostProcessors();
        postProcessors.register(RenderFlowType.RASTERIZATION, new RasterizationPostProcessor());

        // Build packets from all stages
        Map<KeyId, StageExecutionPlan> stagePlans = new LinkedHashMap<>();
        for (GraphicsStage stage : pipeline.getOrderedStages()) {
            GraphicsBatchGroup<C> batchGroup = pipeline.getBatchGroup(stage);
            if (batchGroup != null) {
                StageExecutionPlan stagePlan = batchGroup.createStageExecutionPlan(renderContext, postProcessors);
                if (!stagePlan.isEmpty()) {
                    stagePlans.put(stage.getIdentifier(), stagePlan);
                }
            }
        }

        // Execute uploads on worker if allowed
        RasterizationPostProcessor rasterizationPostProcessor = postProcessors.get(RenderFlowType.RASTERIZATION);
        java.util.List<FrameExecutionPlan.GeometryUploadPlan> geometryUploadPlans = rasterizationPostProcessor != null
                ? rasterizationPostProcessor.geometryUploadPlans()
                : java.util.List.of();
        java.util.List<FrameExecutionPlan.ResourceUploadPlan> resourceUploadPlans = rasterizationPostProcessor != null
                ? rasterizationPostProcessor.resourceUploadPlans()
                : java.util.List.of();

        boolean uploadsCompleted = false;
        if (GraphicsDriver.capabilities().uploadWorkerSupported()) {
            if (GraphicsDriver.runtime().supportsGeometryMaterialization()) {
                // Keep OpenGL raster geometry materialization/upload in the sync/runtime seam.
                postProcessors.executeAllExcept(RenderFlowType.RASTERIZATION);
            } else {
                postProcessors.executeAll();
                uploadsCompleted = true;
            }
        }

        // Publish result
        ctx.kernel().publishBuildResult(new BuildResult(
                ctx.frameNumber(),
                new FrameExecutionPlan(stagePlans, geometryUploadPlans, resourceUploadPlans, null),
                postProcessors,
                uploadsCompleted
        ));
    }
}


