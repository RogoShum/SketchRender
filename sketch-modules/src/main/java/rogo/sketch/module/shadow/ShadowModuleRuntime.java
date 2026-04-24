package rogo.sketch.module.shadow;

import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.SyncPreparePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.LifecyclePhase;
import rogo.sketch.core.pipeline.kernel.ModulePassDefinition;
import rogo.sketch.core.pipeline.kernel.PassExecutionContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphAssemblyContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.setting.SettingChangeEvent;
import rogo.sketch.core.pipeline.shadow.ShadowFrameView;
import rogo.sketch.core.pipeline.shadow.ShadowFrameResources;
import rogo.sketch.core.pipeline.shadow.ShadowPassSnapshot;
import rogo.sketch.core.pipeline.shadow.ShadowPassSnapshotRegistry;
import rogo.sketch.core.pipeline.shadow.ShadowProviderRegistry;
import rogo.sketch.core.resource.ResourceScope;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;
import java.util.function.Consumer;

public class ShadowModuleRuntime implements ModuleRuntime {
    private static final String PASS_CAPTURE = "shadow_capture";

    private final SketchShadowProvider sketchProvider = new SketchShadowProvider();
    private Consumer<SettingChangeEvent> settingListener;
    private volatile boolean kernelInitialized;
    private volatile ShadowPassSnapshot latestShadowPassSnapshot = ShadowPassSnapshot.fallback(ShadowFrameView.unavailable(SketchShadowProvider.PROVIDER_ID));

    @Override
    public String id() {
        return ShadowModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onProcessInit(ModuleRuntimeContext context) {
        settingListener = event -> {
            if (id().equals(event.moduleId())) {
                syncSettings(context);
            }
        };
        context.settings().addListener(settingListener);
        ShadowProviderRegistry.installFallback(sketchProvider);
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        kernelInitialized = true;
        context.registerFrameResourceHandle(ShadowFrameResources.SHADOW_PASS_SNAPSHOT);
        registerMetric(context, ShadowModuleDescriptor.PROVIDER_METRIC, MetricKind.STRING,
                "debug.dashboard.shadow.provider", "debug.dashboard.shadow.provider.detail",
                () -> Objects.toString(currentView().providerId(), "none"));
        registerMetric(context, ShadowModuleDescriptor.AVAILABLE_METRIC, MetricKind.BOOLEAN,
                "debug.dashboard.shadow.available", "debug.dashboard.shadow.available.detail",
                () -> currentView().available());
        registerMetric(context, ShadowModuleDescriptor.PASS_ACTIVE_METRIC, MetricKind.BOOLEAN,
                "debug.dashboard.shadow.pass_active", "debug.dashboard.shadow.pass_active.detail",
                () -> currentView().shadowPassActive());
        registerMetric(context, ShadowModuleDescriptor.TARGET_METRIC, MetricKind.STRING,
                "debug.dashboard.shadow.target", "debug.dashboard.shadow.target.detail",
                () -> shadowTargetSummary(currentView()));
        registerMetric(context, ShadowModuleDescriptor.EPOCH_METRIC, MetricKind.COUNT,
                "debug.dashboard.shadow.epoch", "debug.dashboard.shadow.epoch.detail",
                () -> currentView().epoch());
        context.registerBuiltInResource(
                ResourceTypes.TEXTURE,
                ShadowModuleDescriptor.SHADOW_MAP_TEXTURE,
                () -> {
                    KeyId textureId = currentView().shadowMapTextureId();
                    return textureId != null
                            ? context.resourceManager().getResource(ResourceTypes.TEXTURE, textureId)
                            : null;
                });
        syncSettings(context);
    }

    @Override
    public <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
        builder.addPass(new ShadowCapturePass<>(), SyncPreparePass.NAME);
    }

    @Override
    public void describeFrameResources(ModuleGraphAssemblyContext context) {
        context.registerFrameResourceHandle(ShadowFrameResources.SHADOW_PASS_SNAPSHOT);
    }

    @Override
    public void contributeModulePasses(ModuleGraphAssemblyContext context) {
        context.registerModulePass(new ModulePassDefinition(
                ShadowModuleDescriptor.MODULE_ID,
                PASS_CAPTURE,
                LifecyclePhase.SYNC_PREPARE,
                ThreadDomain.SYNC,
                java.util.List.of(),
                java.util.List.of(ShadowFrameResources.SHADOW_PASS_SNAPSHOT),
                ignored -> {}));
    }

    @Override
    public void onDisable(ModuleRuntimeContext context) {
        syncSettings(context);
    }

    @Override
    public void onShutdown(ModuleRuntimeContext context) {
        kernelInitialized = false;
        if (settingListener != null) {
            context.settings().removeListener(settingListener);
            settingListener = null;
        }
        sketchProvider.clearPublishedResources(context.resourceManager());
        ShadowProviderRegistry.clearProvider(sketchProvider);
        latestShadowPassSnapshot = ShadowPassSnapshot.fallback(ShadowFrameView.unavailable(SketchShadowProvider.PROVIDER_ID));
    }

    private void syncSettings(ModuleRuntimeContext context) {
        boolean ownShadowEnabled = context.isModuleEnabled()
                && context.settings().getBoolean(ShadowModuleDescriptor.OWN_SHADOW_ENABLED, false);
        Object resolutionValue = context.settings().getValue(ShadowModuleDescriptor.SHADOW_RESOLUTION);
        int resolution = resolutionValue instanceof Number number ? number.intValue() : 2048;
        if (!kernelInitialized) {
            if (!ownShadowEnabled) {
                sketchProvider.clearPublishedResources(context.resourceManager());
            }
            return;
        }
        sketchProvider.syncResources(
                context.resourceManager(),
                GraphicsDriver.resourceAllocator(),
                context.ownerId(),
                ResourceScope.MODULE_OWNED,
                ownShadowEnabled,
                resolution);
    }

    private ShadowFrameView currentView() {
        return ShadowProviderRegistry.currentFrameView();
    }

    private ShadowPassSnapshot currentShadowPassSnapshot() {
        return latestShadowPassSnapshot;
    }

    private static String shadowTargetSummary(ShadowFrameView view) {
        if (view == null) {
            return "none";
        }
        String renderTarget = view.renderTargetId() != null ? view.renderTargetId().toString() : "none";
        String texture = view.shadowMapTextureId() != null ? view.shadowMapTextureId().toString() : "none";
        String nativeTarget = view.nativeTargetHandle().isValid()
                ? Long.toString(view.nativeTargetHandle().value())
                : "none";
        return "target=" + renderTarget + ", texture=" + texture + ", nativeTarget=" + nativeTarget
                + ", size=" + view.width() + "x" + view.height();
    }

    private void registerMetric(
            ModuleRuntimeContext context,
            KeyId id,
            MetricKind kind,
            String displayKey,
            String detailKey,
            java.util.function.Supplier<Object> supplier) {
        context.registerMetric(
                new MetricDescriptor(id, ShadowModuleDescriptor.MODULE_ID, kind, displayKey, detailKey),
                supplier);
    }

    private final class ShadowCapturePass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_CAPTURE;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        public void execute(FrameContext<C> ctx) {
            ShadowFrameView shadowView = currentView();
            ShadowPassSnapshot snapshot = ShadowPassSnapshotRegistry.currentSnapshot(ctx.renderContext(), shadowView);
            latestShadowPassSnapshot = snapshot;
            PassExecutionContext passExecutionContext = ctx.passExecutionContext(ShadowModuleDescriptor.MODULE_ID, PASS_CAPTURE);
            passExecutionContext.publish(ShadowFrameResources.SHADOW_PASS_SNAPSHOT, passExecutionContext.frameEpoch(), snapshot);
        }
    }
}
