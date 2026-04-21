package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.data.FrameDataStore;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

/**
 * Merged synchronous preparation pass:
 * <ol>
 *   <li>Reset write buffer (from old SyncCollectDirtyPass)</li>
 *   <li>Install indirect requests</li>
 *   <li>Materialize pending geometry bindings on main thread</li>
 *   <li>Keep the upload-worker capability seam explicit for sync fallback</li>
 * </ol>
 */
public class SyncPreparePass<C extends RenderContext> implements PipelinePass<C> {

    public static final String NAME = "sync_prepare";

    @Override
    public String name() { return NAME; }

    @Override
    public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

    @Override
    public void execute(FrameContext<C> ctx) {
        GraphicsPipeline<C> pipeline = ctx.pipeline();

        // 1. Reset write buffers for all FrameDataStores
        for (PipelineType pipelineType : pipeline.getPipelineTypes()) {
            FrameDataStore frameDataStore = pipeline.getFrameDataStore(pipelineType);
            if (frameDataStore != null) {
                frameDataStore.resetWriteBuffer();
            }
        }
        if (pipeline.runtimeHost() != null) {
            pipeline.runtimeHost().installIndirectRequests(pipeline);
        }

        // 2. Materialize pending geometry bindings on the sync thread.
        // Stage-local entity views and scene caches are prepared in AsyncRenderPass
        // so sync frame work stays focused on resource installation and consumption.
        pipeline.materializePendingGeometryBindings();

        // 3. Sync fallback now only means "materialize backend-owned immediate GL resources".
        // Packet compilation and post-processing no longer depend on legacy RenderFlowStrategy
        // registration.
        if (!GraphicsDriver.capabilities().uploadWorkerSupported()) {
            // No-op: retained to keep the old upload-worker capability seam explicit.
        }
    }
}

