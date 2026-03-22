package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.AsyncTickTransformSource;
import rogo.sketch.core.api.graphics.StaticTransformSource;
import rogo.sketch.core.api.graphics.SyncTickTransformSource;
import rogo.sketch.core.api.graphics.TransformIdAware;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.PostTickAsyncGraphicsPass;
import rogo.sketch.core.pipeline.graph.pass.PostTickGraphicsPass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.kernel.annotation.AsyncOnly;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.transform.TransformBinding;
import rogo.sketch.core.util.transform.TransformUpdateDomain;
import rogo.sketch.core.util.transform.MatrixManager;

/**
 * Tick-driven transform module.
 * <p>
 * The module owns transform bindings and keeps transform work in the tick graph:
 * <ul>
 *   <li>tick-start upload of interpolation data</li>
 *   <li>tick-start transform buffer rotation</li>
 *   <li>post-tick sync transform collection</li>
 *   <li>post-tick async transform collection submission</li>
 * </ul>
 * Render frames only consume the transform data prepared at tick boundaries.
 */
public class TransformModule implements GraphicsModule {
    public static final String MODULE_NAME = "transform";
    private static final String PASS_TICK_UPLOAD = "transform_tick_upload";
    private static final String PASS_TICK_SWAP = "transform_tick_swap";
    private static final String PASS_SYNC_TICK_COLLECT = "transform_sync_tick_collect";
    private static final String PASS_ASYNC_TICK_COLLECT = "transform_async_tick_collect";

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
        return graphics instanceof SyncTickTransformSource
                || graphics instanceof AsyncTickTransformSource
                || graphics instanceof StaticTransformSource;
    }

    @Override
    public void onAttach(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        if (matrixManager == null || matrixManager.isRegistered(graphics)) {
            return;
        }

        TransformUpdateDomain updateDomain = detectUpdateDomain(graphics);
        TransformBinding binding = matrixManager.registerBinding(graphics, updateDomain);
        if (graphics instanceof TransformIdAware idAware) {
            idAware.setTransformId(binding.transformId());
        }
    }

    @Override
    public void onDetach(Graphics graphics) {
        if (matrixManager == null) {
            return;
        }

        TransformBinding binding = matrixManager.bindingFor(graphics);
        if (binding != null) {
            matrixManager.unregisterBinding(binding);
            if (graphics instanceof TransformIdAware idAware) {
                idAware.setTransformId(-1);
            }
        }
    }

    @Override
    public <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
        builder
                .addPreTickPass(new TransformTickUploadPass<>())
                .addPreTickPass(new TransformTickSwapPass<>(), PASS_TICK_UPLOAD)
                .addPostTickPass(new TransformSyncTickPass<>(), PostTickGraphicsPass.NAME)
                .addPostTickPass(new TransformAsyncTickPass<>(), PASS_SYNC_TICK_COLLECT, PostTickAsyncGraphicsPass.NAME);
    }

    @Override
    public <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
        // Transform interpolation data is prepared on tick boundaries.
        // The render graph consumes the uploaded SSBO data and does not
        // rebuild tick-owned transform snapshots.
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

    private TransformUpdateDomain detectUpdateDomain(Graphics graphics) {
        int domainCount = 0;
        TransformUpdateDomain resolved = null;

        if (graphics instanceof SyncTickTransformSource) {
            resolved = TransformUpdateDomain.SYNC_TICK;
            domainCount++;
        }
        if (graphics instanceof AsyncTickTransformSource) {
            resolved = TransformUpdateDomain.ASYNC_TICK;
            domainCount++;
        }
        if (graphics instanceof StaticTransformSource) {
            resolved = TransformUpdateDomain.STATIC;
            domainCount++;
        }

        if (domainCount == 0) {
            throw new IllegalArgumentException("Graphics does not expose any transform authoring domain: "
                    + graphics.getClass().getName());
        }
        if (domainCount > 1) {
            throw new IllegalArgumentException("Graphics exposes multiple transform authoring domains: "
                    + graphics.getClass().getName());
        }
        return resolved;
    }

    // ==================== Internal Passes ====================

    private class TransformTickUploadPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() { return PASS_TICK_UPLOAD; }

        @Override
        public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

        @Override
        @SyncOnly("Upload previous/current tick transform data")
        public void execute(FrameContext<C> ctx) {
            if (matrixManager != null) {
                matrixManager.uploadInterpolationData();
            }
        }
    }

    private class TransformTickSwapPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() { return PASS_TICK_SWAP; }

        @Override
        public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

        @Override
        @SyncOnly("Rotate transform tick buffers")
        public void execute(FrameContext<C> ctx) {
            if (matrixManager != null) {
                matrixManager.swapTickBuffers();
            }
        }
    }

    private class TransformSyncTickPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() { return PASS_SYNC_TICK_COLLECT; }

        @Override
        public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

        @Override
        @SyncOnly("Collect sync transform data on main thread")
        public void execute(FrameContext<C> ctx) {
            if (matrixManager != null) {
                matrixManager.collectSyncTickTransforms();
            }
        }
    }

    private class TransformAsyncTickPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() { return PASS_ASYNC_TICK_COLLECT; }

        @Override
        public ThreadDomain threadDomain() { return ThreadDomain.ASYNC; }

        @Override
        @AsyncOnly("Collect async transform data on tick worker")
        public void execute(FrameContext<C> ctx) {
            if (matrixManager != null) {
                matrixManager.collectAsyncTickTransforms();
            }
        }
    }
}