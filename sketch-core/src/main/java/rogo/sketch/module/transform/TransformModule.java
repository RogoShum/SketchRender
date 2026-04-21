package rogo.sketch.module.transform;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsComponentUpdateCoordinator;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.PostTickGraphicsPass;
import rogo.sketch.core.pipeline.graph.pass.SyncPreparePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.LifecyclePhase;
import rogo.sketch.core.pipeline.kernel.ModulePassDefinition;
import rogo.sketch.core.pipeline.kernel.PassExecutionContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.kernel.annotation.AsyncOnly;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphAssemblyContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.EntityAttachContext;
import rogo.sketch.core.pipeline.module.runtime.GraphicsComponentFilter;
import rogo.sketch.core.pipeline.module.runtime.GraphicsEntitySnapshot;
import rogo.sketch.core.pipeline.module.runtime.ModuleSubscriptionRegistrar;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.transform.manager.TransformManager;

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

    private GraphicsComponentUpdateCoordinator updateCoordinator;

    @Override
    public String id() {
        return MODULE_NAME;
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        this.updateCoordinator = new GraphicsComponentUpdateCoordinator();
        context.registerFrameResourceHandle(TransformManager.TICK_SNAPSHOT_HANDLE);
        context.registerMetric(new MetricDescriptor(
                TransformModuleDescriptor.ACTIVE_COUNT_METRIC,
                MODULE_NAME,
                MetricKind.COUNT,
                "metric." + MODULE_NAME + ".active_count",
                "metric." + MODULE_NAME + ".active_count.detail"), this::getActiveCount);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of("sketch_render", "transform_input_async"),
                () -> matrixManager() != null ? matrixManager().getAsyncPipeline().inputSSBO() : null);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of("sketch_render", "transform_input_sync"),
                () -> matrixManager() != null ? matrixManager().getSyncPipeline().inputSSBO() : null);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of("sketch_render", "transform_output"),
                () -> matrixManager() != null ? matrixManager().getOutputSSBO() : null);
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
    public void registerEntitySubscriptions(ModuleSubscriptionRegistrar registrar) {
        registrar.register(
                "lifecycle_binding",
                GraphicsComponentFilter.builder()
                        .require(GraphicsBuiltinComponents.LIFECYCLE)
                        .require(GraphicsBuiltinComponents.LIFECYCLE_BINDING)
                        .build());
        registrar.register(
                "transform_binding",
                GraphicsComponentFilter.builder()
                        .require(GraphicsBuiltinComponents.LIFECYCLE)
                        .require(GraphicsBuiltinComponents.TRANSFORM_BINDING)
                        .build());
        registrar.register(
                "bounds_binding",
                GraphicsComponentFilter.builder()
                        .require(GraphicsBuiltinComponents.LIFECYCLE)
                        .require(GraphicsBuiltinComponents.BOUNDS_BINDING)
                        .build());
        registrar.register(
                "graphics_tags_binding",
                GraphicsComponentFilter.builder()
                        .require(GraphicsBuiltinComponents.LIFECYCLE)
                        .require(GraphicsBuiltinComponents.GRAPHICS_TAGS_BINDING)
                        .build());
        registrar.register(
                "object_flags_binding",
                GraphicsComponentFilter.builder()
                        .require(GraphicsBuiltinComponents.LIFECYCLE)
                        .require(GraphicsBuiltinComponents.OBJECT_FLAGS_BINDING)
                        .build());
    }

    @Override
    public void onEntityAttached(GraphicsEntityId entityId, GraphicsEntitySnapshot snapshot, EntityAttachContext context) {
        if (updateCoordinator == null || entityId == null || snapshot == null) {
            return;
        }
        updateCoordinator.registerEntity(context.graphicsWorld(), entityId, snapshot);
    }

    @Override
    public void onEntityDetached(GraphicsEntityId entityId, EntityAttachContext context) {
        if (updateCoordinator == null || entityId == null) {
            return;
        }
        updateCoordinator.unregisterEntity(context.graphicsWorld(), entityId);
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
    public void describeFrameResources(ModuleGraphAssemblyContext context) {
        context.registerFrameResourceHandle(TransformManager.TICK_SNAPSHOT_HANDLE);
    }

    @Override
    public void contributeModulePasses(ModuleGraphAssemblyContext context) {
        context.registerModulePass(new ModulePassDefinition(
                MODULE_NAME,
                PASS_TICK_SWAP,
                LifecyclePhase.SYNC_PREPARE,
                ThreadDomain.SYNC,
                java.util.List.of(),
                java.util.List.of(),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                MODULE_NAME,
                PASS_SYNC_TICK_COLLECT,
                LifecyclePhase.SYNC_PRE_BUILD,
                ThreadDomain.SYNC,
                java.util.List.of(),
                java.util.List.of(),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                MODULE_NAME,
                PASS_ASYNC_TICK_COLLECT,
                LifecyclePhase.ASYNC_POST_BUILD,
                ThreadDomain.ASYNC,
                java.util.List.of(),
                java.util.List.of(TransformManager.TICK_SNAPSHOT_HANDLE),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                MODULE_NAME,
                PASS_FRAME_COLLECT,
                LifecyclePhase.SYNC_PREPARE,
                ThreadDomain.SYNC,
                java.util.List.of(TransformManager.TICK_SNAPSHOT_HANDLE),
                java.util.List.of(),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                MODULE_NAME,
                PASS_FRAME_UPLOAD,
                LifecyclePhase.SYNC_PRE_BUILD,
                ThreadDomain.SYNC,
                java.util.List.of(TransformManager.TICK_SNAPSHOT_HANDLE),
                java.util.List.of(),
                ignored -> {}));
    }

    @Override
    public void onShutdown(ModuleRuntimeContext context) {
        if (updateCoordinator != null) {
            updateCoordinator.cleanup();
            updateCoordinator = null;
        }
    }

    public TransformManager matrixManager() {
        return updateCoordinator != null ? updateCoordinator.transformManager() : null;
    }

    public @Nullable GraphicsComponentUpdateCoordinator updateCoordinator() {
        return updateCoordinator;
    }

    private int getActiveCount() {
        return matrixManager() != null ? matrixManager().getActiveCount() : 0;
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
            if (updateCoordinator != null) {
                updateCoordinator.swapTickBuffers();
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
            if (updateCoordinator != null) {
                updateCoordinator.collectSyncTick();
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
            if (updateCoordinator != null) {
                updateCoordinator.collectAsyncTick();
                updateCoordinator.applyConcreteComponents(ctx.pipeline().graphicsWorld());
                PassExecutionContext passExecutionContext = ctx.passExecutionContext(MODULE_NAME, PASS_ASYNC_TICK_COLLECT);
                updateCoordinator.prepareAndPublishTickSnapshot(passExecutionContext);
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
            if (updateCoordinator != null) {
                updateCoordinator.collectFrame();
                updateCoordinator.applyConcreteComponents(ctx.pipeline().graphicsWorld());
                PassExecutionContext passExecutionContext = ctx.passExecutionContext(MODULE_NAME, PASS_FRAME_COLLECT);
                updateCoordinator.prepareFrameBuffer(passExecutionContext);
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
            if (updateCoordinator != null) {
                PassExecutionContext passExecutionContext = ctx.passExecutionContext(MODULE_NAME, PASS_FRAME_UPLOAD);
                updateCoordinator.uploadFrameBuffers(passExecutionContext);
            }
        }
    }
}
