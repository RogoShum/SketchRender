package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.BuildResult;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
        GraphicsPipeline<C> pipeline = ctx.pipeline();

        BuildResult buildResult = ctx.kernel().consumeBuildResult();
        if (buildResult == null) return;

        FrameExecutionPlan executionPlan = buildResult.executionPlan();
        RenderPostProcessors postProcessors = buildResult.postProcessors();

        // If uploads were NOT done on worker, execute post-processors on main thread now
        boolean geometryHandled = false;
        if (!executionPlan.geometryUploadPlans().isEmpty()) {
            geometryHandled = rogo.sketch.core.driver.GraphicsDriver.runtime()
                    .installGeometryUploads(pipeline, executionPlan, !buildResult.uploadsCompleted());
        }
        if (!geometryHandled && !buildResult.uploadsCompleted()) {
            for (FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan : executionPlan.geometryUploadPlans()) {
                if (geometryUploadPlan != null) {
                    geometryUploadPlan.releaseBuilderSnapshots();
                }
            }
        }
        if (!buildResult.uploadsCompleted() && postProcessors != null) {
            postProcessors.executeAllExcept(RenderFlowType.RASTERIZATION);
        }

        // Materialize any VAOs that were queued during async build
        pipeline.materializePendingGeometryBindings();

        // Swap double-buffered data stores
        pipeline.swapFrameDataStores();

        for (PipelineType pipelineType : ctx.pipeline().getPipelineTypes()) {
            PipelineDataStore readStore = ctx.pipeline().getPipelineDataStore(pipelineType, FrameDataDomain.SYNC_READ);
            PipelineDataStore writeStore = ctx.pipeline().getPipelineDataStore(pipelineType, FrameDataDomain.ASYNC_BUILD);
            IndirectBufferData readIndirectBufferData = readStore.get(IndirectBufferData.KEY);
            IndirectBufferData writeIndirectBufferData = writeStore.get(IndirectBufferData.KEY);
            if (readIndirectBufferData != null) {
                readIndirectBufferData.materializePending();
            }
            if (writeIndirectBufferData != null) {
                writeIndirectBufferData.materializePending();
            }
            if (readIndirectBufferData != null && writeIndirectBufferData != null) {
                for (rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter : readIndirectBufferData.getAll().keySet()) {
                    writeIndirectBufferData.getOrCreate(renderParameter);
                }
                for (rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter : writeIndirectBufferData.getAll().keySet()) {
                    readIndirectBufferData.getOrCreate(renderParameter);
                }
            }
        }

        // Commit packets to render packet queue
        RenderPacketQueue<C> queue = pipeline.getRenderPacketQueue();
        rogo.sketch.core.driver.GraphicsDriver.runtime().installExecutionPlan(executionPlan);
        queue.installExecutionPlan(executionPlan);
    }
}

