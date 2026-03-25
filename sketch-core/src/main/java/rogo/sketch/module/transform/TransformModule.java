package rogo.sketch.module.transform;

import rogo.sketch.core.api.graphics.*;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.PostTickGraphicsPass;
import rogo.sketch.core.pipeline.graph.pass.SyncPreparePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.kernel.annotation.AsyncOnly;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.transform.manager.TransformManager;
import rogo.sketch.module.transform.manager.TransformBinding;
import rogo.sketch.module.transform.manager.TransformUpdateDomain;

/**
 * Mixed tick/frame transform module.
 * <p>
 * The module owns transform bindings and keeps tick-authored snapshots separate
 * from render-owned GPU upload state:
 * <ul>
 *   <li>tick-start transform buffer rotation</li>
 *   <li>post-tick sync transform collection</li>
 *   <li>post-tick async transform collection and snapshot publication</li>
 *   <li>frame-pass collection for explicit SYNC_FRAME authors</li>
 *   <li>frame-pass snapshot consumption and SSBO upload</li>
 * </ul>
 */
public class TransformModule implements ModuleRuntime {
    public static final String MODULE_NAME = "transform";
    private static final String PASS_TICK_SWAP = "transform_tick_swap";
    private static final String PASS_SYNC_TICK_COLLECT = "transform_sync_tick_collect";
    private static final String PASS_ASYNC_TICK_COLLECT = "transform_async_tick_collect";
    private static final String PASS_FRAME_COLLECT = "transform_frame_collect";
    private static final String PASS_FRAME_UPLOAD = "transform_frame_upload";

    private TransformManager transformManager;

    @Override
    public String id() {
        return MODULE_NAME;
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        this.transformManager = new TransformManager();
        context.registerMetric(new MetricDescriptor(
                TransformModuleDescriptor.ACTIVE_COUNT_METRIC,
                MODULE_NAME,
                MetricKind.COUNT,
                "metric." + MODULE_NAME + ".active_count",
                "metric." + MODULE_NAME + ".active_count.detail"), this::getActiveCount);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of("sketch_render", "transform_input_async"),
                () -> transformManager != null ? transformManager.getAsyncPipeline().inputSSBO() : null);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of("sketch_render", "transform_input_sync"),
                () -> transformManager != null ? transformManager.getSyncPipeline().inputSSBO() : null);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of("sketch_render", "transform_output"),
                () -> transformManager != null ? transformManager.getOutputSSBO() : null);
        context.registerUniform(KeyId.of("u_transformCount"), ValueGetter.create((instance) -> {
            RenderContext renderContext = (RenderContext) instance;
            if (renderContext.transformModule() != null && renderContext.transformModule().matrixManager() != null) {
                return renderContext.transformModule().matrixManager().getActiveCount();
            }
            return 0;
        }, Integer.class, RenderContext.class));
        context.registerUniform(KeyId.of("u_batchOffset"), ValueGetter.create(() -> 0, Integer.class));
        context.registerUniform(KeyId.of("u_batchCount"), ValueGetter.create(() -> 0, Integer.class));
    }

    @Override
    public boolean supports(Graphics graphics) {
        return graphics instanceof SyncTickTransformSource
                || graphics instanceof AsyncTickTransformSource
                || graphics instanceof FrameTransformSource
                || graphics instanceof StaticTransformSource;
    }

    @Override
    public void onGraphicsAttached(Graphics graphics, rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter, KeyId containerType, ModuleRuntimeContext context) {
        if (transformManager == null || transformManager.isRegistered(graphics)) {
            return;
        }

        TransformUpdateDomain updateDomain = detectUpdateDomain(graphics);
        TransformBinding binding = transformManager.registerBinding(graphics, updateDomain);
        if (graphics instanceof TransformIdAware idAware) {
            idAware.setTransformId(binding.transformId());
        }
    }

    @Override
    public void onGraphicsDetached(Graphics graphics, ModuleRuntimeContext context) {
        if (transformManager == null) {
            return;
        }

        TransformBinding binding = transformManager.bindingFor(graphics);
        if (binding != null) {
            transformManager.unregisterBinding(binding);
            if (graphics instanceof TransformIdAware idAware) {
                idAware.setTransformId(-1);
            }
        }
    }

    @Override
    public <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
        builder.addPreTickPass(new TransformTickSwapPass<>())
                .addPostTickPass(new TransformSyncTickPass<>(), PostTickGraphicsPass.NAME)
                .addPostTickGlAsyncPass(new TransformAsyncTickPass<>());
    }

    @Override
    public <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
        builder.addPass(new TransformFrameCollectPass<>(), SyncPreparePass.NAME);
        builder.addPass(new TransformFrameUploadPass<>(), PASS_FRAME_COLLECT);
    }

    @Override
    public void onShutdown(ModuleRuntimeContext context) {
        if (transformManager != null) {
            transformManager.cleanup();
            transformManager = null;
        }
    }

    public TransformManager matrixManager() {
        return transformManager;
    }

    private int getActiveCount() {
        return transformManager != null ? transformManager.getActiveCount() : 0;
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
        if (graphics instanceof FrameTransformSource) {
            resolved = TransformUpdateDomain.SYNC_FRAME;
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

    private class TransformTickSwapPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_TICK_SWAP;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Rotate transform tick buffers")
        public void execute(FrameContext<C> ctx) {
            if (transformManager != null) {
                transformManager.swapTickBuffers();
            }
        }
    }

    private class TransformSyncTickPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_SYNC_TICK_COLLECT;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Collect sync transform data on main thread")
        public void execute(FrameContext<C> ctx) {
            if (transformManager != null) {
                transformManager.collectSyncTickTransforms();
            }
        }
    }

    private class TransformAsyncTickPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_ASYNC_TICK_COLLECT;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.ASYNC;
        }

        @Override
        @AsyncOnly("Collect async transform data on tick worker")
        public void execute(FrameContext<C> ctx) {
            if (transformManager != null) {
                transformManager.collectAsyncTickTransforms();
                transformManager.prepareAndPublishTickSnapshot(ctx.kernel(), ctx.logicTickEpoch());
            }
        }
    }

    private class TransformFrameCollectPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_FRAME_COLLECT;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Collect frame-authored transform data on main thread")
        public void execute(FrameContext<C> ctx) {
            if (transformManager != null) {
                transformManager.collectFrameTransforms();
                transformManager.prepareFrameBuffer(ctx.kernel());
            }
        }
    }

    private class TransformFrameUploadPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_FRAME_UPLOAD;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Upload transform SSBOs after frame collect")
        public void execute(FrameContext<C> ctx) {
            if (transformManager != null) {
                transformManager.uploadFrameBuffers();
            }
        }
    }
}