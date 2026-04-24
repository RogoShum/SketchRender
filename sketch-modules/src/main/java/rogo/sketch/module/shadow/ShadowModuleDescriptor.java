package rogo.sketch.module.shadow;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.setting.ChangeImpact;
import rogo.sketch.core.util.KeyId;

public class ShadowModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "shadow";
    public static final String SHADOW_PASS_MACRO = "SKETCH_SHADOW_PASS";

    public static final KeyId GROUP_GENERAL = KeyId.of("sketch_render", "shadow_general");
    public static final KeyId SHADOW_DEPTH_STAGE_ID = KeyId.of("sketch_render", "shadow_depth_stage");
    public static final KeyId OWN_SHADOW_ENABLED = KeyId.of("sketch_render", "shadow_own_enabled");
    public static final KeyId SHADOW_RESOLUTION = KeyId.of("sketch_render", "shadow_resolution");
    public static final KeyId SHADOW_MAP_TEXTURE = KeyId.of("sketch_render", "shadow_map");
    public static final KeyId SHADOW_DEPTH_TEXTURE = KeyId.of("sketch_render", "shadow_depth");
    public static final KeyId SHADOW_RENDER_TARGET = KeyId.of("sketch_render", "shadow_target");

    public static final KeyId PROVIDER_METRIC = KeyId.of("sketch_render", "shadow_provider");
    public static final KeyId AVAILABLE_METRIC = KeyId.of("sketch_render", "shadow_available");
    public static final KeyId PASS_ACTIVE_METRIC = KeyId.of("sketch_render", "shadow_pass_active");
    public static final KeyId TARGET_METRIC = KeyId.of("sketch_render", "shadow_target");
    public static final KeyId EPOCH_METRIC = KeyId.of("sketch_render", "shadow_epoch");

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 260;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
        var settings = context.settingsDsl();
        var macros = context.macrosDsl();

        settings.group(GROUP_GENERAL, "sketch_render.shadow")
                .parent(context.moduleEnabledSettingId())
                .register();
        settings.bool(OWN_SHADOW_ENABLED, "sketch_render.shadow.own_enabled")
                .detail("sketch_render.detail.shadow.own_enabled")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.REBUILD_GRAPHS)
                .defaultValue(false)
                .register();
        settings.integer(SHADOW_RESOLUTION, "sketch_render.shadow.resolution")
                .detail("sketch_render.detail.shadow.resolution")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.RECREATE_SESSION_RESOURCES)
                .defaultValue(2048)
                .number(256, 8192, 256, "%d")
                .register();

        macros.flag(SHADOW_PASS_MACRO)
                .displayKey("sketch_render.shadow.pass_macro")
                .detail("sketch_render.detail.shadow.pass_macro")
                .register();

        context.registerMetricDescriptor(new MetricDescriptor(
                PROVIDER_METRIC,
                MODULE_ID,
                MetricKind.STRING,
                "debug.dashboard.shadow.provider",
                "debug.dashboard.shadow.provider.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                AVAILABLE_METRIC,
                MODULE_ID,
                MetricKind.BOOLEAN,
                "debug.dashboard.shadow.available",
                "debug.dashboard.shadow.available.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                PASS_ACTIVE_METRIC,
                MODULE_ID,
                MetricKind.BOOLEAN,
                "debug.dashboard.shadow.pass_active",
                "debug.dashboard.shadow.pass_active.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                TARGET_METRIC,
                MODULE_ID,
                MetricKind.STRING,
                "debug.dashboard.shadow.target",
                "debug.dashboard.shadow.target.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                EPOCH_METRIC,
                MODULE_ID,
                MetricKind.COUNT,
                "debug.dashboard.shadow.epoch",
                "debug.dashboard.shadow.epoch.detail"));
    }

    @Override
    public ShadowModuleRuntime createRuntime() {
        return new ShadowModuleRuntime();
    }
}
