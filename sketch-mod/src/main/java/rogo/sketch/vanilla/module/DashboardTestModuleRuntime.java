package rogo.sketch.vanilla.module;

import rogo.sketch.core.backend.RuntimeDebugToggles;
import rogo.sketch.core.pipeline.module.macro.MacroChoiceTarget;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroProjector;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.setting.SettingChangeEvent;
import rogo.sketch.core.shader.config.MacroEntryDescriptor;
import rogo.sketch.core.shader.config.MacroEntryType;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class DashboardTestModuleRuntime implements ModuleRuntime {
    private static final KeyId HEARTBEAT_METRIC = KeyId.of("sketch_render", "dashboard_test_heartbeat");
    private static final KeyId SWITCH_METRIC = KeyId.of("sketch_render", "dashboard_test_switch");
    private static final KeyId STRING_METRIC = KeyId.of("sketch_render", "dashboard_test_mode_name");

    private final ModuleMacroProjector macroProjector = new ModuleMacroProjector()
            .projectFlag(DashboardTestModuleDescriptor.MACRO_BOOL, "SKETCH_TEST_BOOL")
            .projectValue(DashboardTestModuleDescriptor.MACRO_VALUE, "SKETCH_TEST_SCALE",
                    value -> String.format(Locale.ROOT, "%.1f", ((Number) value).doubleValue()))
            .projectChoice(
                    DashboardTestModuleDescriptor.MACRO_CHOICE,
                    value -> value instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(value),
                    List.of(
                            new MacroChoiceTarget(DashboardTestModuleDescriptor.MacroChoiceMode.LAB_PBR.name(), "SKETCH_TEST_PBR_LAB", null),
                            new MacroChoiceTarget(DashboardTestModuleDescriptor.MacroChoiceMode.SEUS_PBR.name(), "SKETCH_TEST_PBR_SEUS", null),
                            new MacroChoiceTarget(DashboardTestModuleDescriptor.MacroChoiceMode.STANDARD_PBR.name(), "SKETCH_TEST_PBR_STANDARD", null),
                            new MacroChoiceTarget(DashboardTestModuleDescriptor.MacroChoiceMode.VANILLA.name(), "SKETCH_TEST_PBR_VANILLA", null)));
    private Consumer<SettingChangeEvent> settingListener;

    @Override
    public String id() {
        return DashboardTestModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onProcessInit(ModuleRuntimeContext context) {
        context.diagnostics().debug(id(), "Dashboard test module initialized");
        context.diagnostics().info(id(), "Dashboard test controls are available in development mode");
        applyRuntimeToggles(context);
        settingListener = event -> {
            if (id().equals(event.moduleId())) {
                applyRuntimeToggles(context);
                applyMacros(context);
            }
        };
        context.settings().addListener(settingListener);
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        registerRuntimeOwnedState(context);
        applyRuntimeToggles(context);
        applyMacros(context);
    }

    @Override
    public void onEnable(ModuleRuntimeContext context) {
        context.diagnostics().info(id(), "Dashboard test module enabled");
        registerRuntimeOwnedState(context);
        applyRuntimeToggles(context);
        applyMacros(context);
    }

    @Override
    public void onResourceReload(ModuleRuntimeContext context) {
        context.diagnostics().debug(id(), "Dashboard test module observed resource reload");
        registerConstantMacros(context);
        applyMacros(context);
    }

    @Override
    public void onDisable(ModuleRuntimeContext context) {
        context.diagnostics().warn(id(), "Dashboard test module disabled");
        RuntimeDebugToggles.reset();
        context.clearOwnedMacros();
    }

    @Override
    public void onShutdown(ModuleRuntimeContext context) {
        if (settingListener != null) {
            context.settings().removeListener(settingListener);
            settingListener = null;
        }
        RuntimeDebugToggles.reset();
        context.clearOwnedMacros();
    }

    private void registerRuntimeOwnedState(ModuleRuntimeContext context) {
        registerMetrics(context);
        registerConstantMacros(context);
    }

    private void registerMetrics(ModuleRuntimeContext context) {
        context.registerMetric(new MetricDescriptor(HEARTBEAT_METRIC, id(), MetricKind.FLOAT, "debug.dashboard.test.metric.heartbeat", null),
                () -> (System.currentTimeMillis() % 10_000L) / 1000.0D);
        context.registerMetric(new MetricDescriptor(SWITCH_METRIC, id(), MetricKind.BOOLEAN, "debug.dashboard.test.metric.switch", null),
                () -> context.settings().getBoolean(DashboardTestModuleDescriptor.TOGGLE, false));
        context.registerMetric(new MetricDescriptor(STRING_METRIC, id(), MetricKind.STRING, "debug.dashboard.test.metric.mode_name", null),
                () -> {
                    Object value = context.settings().getValue(DashboardTestModuleDescriptor.MACRO_CHOICE);
                    if (value instanceof Enum<?> enumValue) {
                        return enumValue.name();
                    }
                    return value != null ? value.toString() : "-";
                });
    }

    private void registerConstantMacros(ModuleRuntimeContext context) {
        context.macros().setMacro(context.ownerId(), "SKETCH_TEST_CONST_INT", "64",
                new MacroEntryDescriptor("SKETCH_TEST_CONST_INT", MacroEntryType.CONSTANT, false, "64",
                        "debug.dashboard.test.constant_int", null, "debug.dashboard.test.constant_int.detail", null));
        context.macros().setFlag(context.ownerId(), "SKETCH_TEST_CONST_FLAG", true,
                new MacroEntryDescriptor("SKETCH_TEST_CONST_FLAG", MacroEntryType.CONSTANT, false, "1",
                        "debug.dashboard.test.constant_flag", null, "debug.dashboard.test.constant_flag.detail", null));
    }

    private void applyMacros(ModuleRuntimeContext context) {
        macroProjector.apply(context.ownerId(), context.settings().snapshot(), context.macros());
    }

    private void applyRuntimeToggles(ModuleRuntimeContext context) {
        RuntimeDebugToggles.setGlAsyncGpuWorkersDisabled(
                context.settings().getBoolean(DashboardTestModuleDescriptor.DISABLE_GL_ASYNC_GPU_WORKERS, false));
    }
}
