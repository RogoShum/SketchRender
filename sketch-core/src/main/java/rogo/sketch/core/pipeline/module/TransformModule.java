package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.TransformableGraphics;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.SyncCommitPass;
import rogo.sketch.core.pipeline.graph.pass.SyncPreparePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.kernel.annotation.AsyncOnly;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.transform.Transform;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.transform.MatrixManager;

/**
 * Module that manages the Transform system (MatrixManager) within the new architecture.
 * <p>
 * Replaces the old {@code TransformStateManager} + {@code ContainerListener} pattern
 * with explicit module attachment and render-graph passes for:
 * <ul>
 *   <li>Sync tick transform data processing</li>
 *   <li>Async tick transform data processing</li>
 *   <li>Pre-render GPU upload and compute dispatch</li>
 * </ul>
 * </p>
 */
public class TransformModule implements GraphicsModule {
    public static final String MODULE_NAME = "transform";
    private static final String PASS_SYNC_TICK = "transform_sync_tick";
    private static final String PASS_ASYNC_TICK = "transform_async_tick";
    private static final String PASS_UPLOAD = "transform_upload";

    private MatrixManager matrixManager;

    @Override
    public String name() { return MODULE_NAME; }

    @Override
    public int priority() { return 100; } // Initialize early

    @Override
    public void initialize(GraphicsPipeline<?> pipeline) {
        this.matrixManager = new MatrixManager();
    }

    @Override
    public boolean supports(Graphics graphics) {
        return graphics instanceof TransformableGraphics tg && tg.hasTransform();
    }

    @Override
    public void onAttach(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        if (graphics instanceof TransformableGraphics tg && tg.hasTransform()) {
            Transform transform = tg.getTransform();
            if (!matrixManager.isRegistered(transform)) {
                matrixManager.registerTransform(transform);
            }
        }
    }

    @Override
    public void onDetach(Graphics graphics) {
        if (graphics instanceof TransformableGraphics tg && tg.hasTransform()) {
            Transform transform = tg.getTransform();
            if (matrixManager.isRegistered(transform)) {
                matrixManager.unregisterTransform(transform);
            }
        }
    }

    @Override
    public <C extends RenderContext> void contributeToGraph(RenderGraphBuilder<C> builder) {
        builder
                .addPass(new TransformSyncTickPass<>(), SyncCommitPass.NAME)
                .addPass(new TransformAsyncTickPass<>(), PASS_SYNC_TICK)
                // Upload after sync prepare; do not block on async render in cross-frame mode.
                .addPass(new TransformUploadPass<>(), PASS_ASYNC_TICK, SyncPreparePass.NAME);
    }

    @Override
    public void cleanup() {
        if (matrixManager != null) {
            matrixManager.cleanup();
            matrixManager = null;
        }
    }

    public MatrixManager matrixManager() {
        return matrixManager;
    }

    // ==================== Internal Passes ====================

    private class TransformSyncTickPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() { return PASS_SYNC_TICK; }

        @Override
        public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

        @Override
        @SyncOnly("Transform sync tick on main thread")
        public void execute(FrameContext<C> ctx) {
            if (matrixManager != null) {
                matrixManager.syncTick();
            }
        }
    }

    private class TransformAsyncTickPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() { return PASS_ASYNC_TICK; }

        @Override
        public ThreadDomain threadDomain() { return ThreadDomain.ASYNC; }

        @Override
        @AsyncOnly("Transform async tick on worker thread")
        public void execute(FrameContext<C> ctx) {
            if (matrixManager != null) {
                matrixManager.asyncTick();
                matrixManager.swapTransformData();
            }
        }
    }

    /**
     * Sync pass that uploads transform data and dispatches compute shader.
     */
    private class TransformUploadPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() { return PASS_UPLOAD; }

        @Override
        public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

        @Override
        @SyncOnly("GPU upload and compute dispatch")
        public void execute(FrameContext<C> ctx) {
            if (matrixManager != null) {
                matrixManager.preRender();
            }
        }
    }
}