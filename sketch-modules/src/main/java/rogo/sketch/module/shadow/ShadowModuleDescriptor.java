package rogo.sketch.module.shadow;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.setting.ChangeImpact;
import rogo.sketch.core.pipeline.shadow.ShadowProfile;
import rogo.sketch.core.ui.control.ChoiceOptionSpec;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public class ShadowModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "shadow";
    public static final String SHADOW_PASS_MACRO = "SKETCH_SHADOW_PASS";
    public static final String SHADOW_COLOR0_ALIAS = "shadow_color0";

    public static final KeyId GROUP_GENERAL = KeyId.of("sketch_render", "shadow_general");
    public static final KeyId SHADOW_DEPTH_STAGE_ID = KeyId.of("sketch_render", "shadow_depth_stage");
    public static final KeyId OWN_SHADOW_ENABLED = KeyId.of("sketch_render", "shadow_own_enabled");
    public static final KeyId SHADOW_RESOLUTION = KeyId.of("sketch_render", "shadow_resolution");
    public static final KeyId SHADOW_PROFILE = KeyId.of("sketch_render", "shadow_profile");
    public static final KeyId SHADOW_MAP_TEXTURE = KeyId.of("sketch_render", "shadow_map");
    public static final KeyId SHADOW_DEPTH_TEXTURE = KeyId.of("sketch_render", "shadow_depth");
    public static final KeyId SHADOW_COLOR0_TEXTURE = KeyId.of("sketch_render", SHADOW_COLOR0_ALIAS);
    public static final KeyId SHADOW_RENDER_TARGET = KeyId.of("sketch_render", "shadow_target");

    public static final KeyId PROVIDER_METRIC = KeyId.of("sketch_render", "shadow_provider");
    public static final KeyId AVAILABLE_METRIC = KeyId.of("sketch_render", "shadow_available");
    public static final KeyId PASS_ACTIVE_METRIC = KeyId.of("sketch_render", "shadow_pass_active");
    public static final KeyId TARGET_METRIC = KeyId.of("sketch_render", "shadow_target");
    public static final KeyId EPOCH_METRIC = KeyId.of("sketch_render", "shadow_epoch");
    public static final KeyId PROFILE_METRIC = KeyId.of("sketch_render", "shadow_profile_metric");
    public static final KeyId EXPORTED_ATTACHMENTS_METRIC = KeyId.of("sketch_render", "shadow_exported_attachments");
    public static final KeyId COLOR0_BOUND_METRIC = KeyId.of("sketch_render", "shadow_color0_bound");

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
        settings.enumeration(SHADOW_PROFILE, "debug.dashboard.shadow.profile", ShadowProfile.class)
                .detail("debug.dashboard.shadow.profile.detail")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.RECREATE_SESSION_RESOURCES)
                .defaultValue(ShadowProfile.DEPTH_ONLY)
                .values(List.of(ShadowProfile.DEPTH_ONLY, ShadowProfile.DEPTH_PLUS_COLOR0))
                .choice(new ChoiceSpec(
                        List.of(
                                new ChoiceOptionSpec(
                                        ShadowProfile.DEPTH_ONLY.name(),
                                        "debug.dashboard.shadow.profile.depth_only",
                                        null,
                                        "debug.dashboard.shadow.profile.depth_only.detail"),
                                new ChoiceOptionSpec(
                                        ShadowProfile.DEPTH_PLUS_COLOR0.name(),
                                        "debug.dashboard.shadow.profile.depth_plus_color0",
                                        null,
                                        "debug.dashboard.shadow.profile.depth_plus_color0.detail")),
                        ChoicePresentation.SEGMENTED))
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
        context.registerMetricDescriptor(new MetricDescriptor(
                PROFILE_METRIC,
                MODULE_ID,
                MetricKind.STRING,
                "debug.dashboard.shadow.profile",
                "debug.dashboard.shadow.profile.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                EXPORTED_ATTACHMENTS_METRIC,
                MODULE_ID,
                MetricKind.STRING,
                "debug.dashboard.shadow.exported_attachments",
                "debug.dashboard.shadow.exported_attachments.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                COLOR0_BOUND_METRIC,
                MODULE_ID,
                MetricKind.BOOLEAN,
                "debug.dashboard.shadow.color0_bound",
                "debug.dashboard.shadow.color0_bound.detail"));
    }

    @Override
    public ShadowModuleRuntime createRuntime() {
        return new ShadowModuleRuntime();
    }
}
