package rogo.sketch.vanilla.module;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.setting.ChangeImpact;
import rogo.sketch.core.ui.control.ChoiceOptionSpec;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public class DashboardTestModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "dashboard_test";

    public static final KeyId GROUP = KeyId.of("sketch_render", "dashboard_test_group");
    public static final KeyId TOGGLE = KeyId.of("sketch_render", "dashboard_test_toggle");
    public static final KeyId SLIDER = KeyId.of("sketch_render", "dashboard_test_slider");
    public static final KeyId NUMBER = KeyId.of("sketch_render", "dashboard_test_number");
    public static final KeyId MODE_TWO = KeyId.of("sketch_render", "dashboard_test_mode_two");
    public static final KeyId MODE_THREE = KeyId.of("sketch_render", "dashboard_test_mode_three");
    public static final KeyId MODE_FOUR = KeyId.of("sketch_render", "dashboard_test_mode_four");
    public static final KeyId MACRO_BOOL = KeyId.of("sketch_render", "dashboard_test_macro_bool");
    public static final KeyId MACRO_VALUE = KeyId.of("sketch_render", "dashboard_test_macro_value");
    public static final KeyId MACRO_CHOICE = KeyId.of("sketch_render", "dashboard_test_macro_choice");

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 950;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
        var settings = context.settingsDsl();
        var macros = context.macrosDsl();

        settings.group(GROUP, "debug.dashboard.group.testing")
                .parent(context.moduleEnabledSettingId())
                .register();

        settings.bool(TOGGLE, "debug.dashboard.test.toggle")
                .detail("debug.dashboard.test.toggle.detail")
                .parent(GROUP)
                .impact(ChangeImpact.RUNTIME_ONLY)
                .defaultValue(true)
                .register();

        settings.floating(SLIDER, "debug.dashboard.test.slider")
                .detail("debug.dashboard.test.slider.detail")
                .parent(GROUP)
                .impact(ChangeImpact.RUNTIME_ONLY)
                .defaultValue(0.35f)
                .slider(0.0f, 1.0f, 0.05f, "%.2f")
                .register();

        settings.integer(NUMBER, "debug.dashboard.test.number")
                .detail("debug.dashboard.test.number.detail")
                .parent(GROUP)
                .impact(ChangeImpact.RUNTIME_ONLY)
                .defaultValue(8)
                .number(0, 32, 1, "%d")
                .register();

        settings.enumeration(MODE_TWO, "debug.dashboard.test.mode_two", TwoMode.class)
                .detail("debug.dashboard.test.mode_two.detail")
                .parent(GROUP)
                .defaultValue(TwoMode.LEFT)
                .choice(new ChoiceSpec(List.of(
                        new ChoiceOptionSpec(TwoMode.LEFT.name(), "debug.dashboard.test.mode_two.left", null, null),
                        new ChoiceOptionSpec(TwoMode.RIGHT.name(), "debug.dashboard.test.mode_two.right", null, null)),
                        ChoicePresentation.AUTO))
                .register();

        settings.enumeration(MODE_THREE, "debug.dashboard.test.mode_three", ThreeMode.class)
                .detail("debug.dashboard.test.mode_three.detail")
                .parent(GROUP)
                .defaultValue(ThreeMode.LOW)
                .choice(new ChoiceSpec(List.of(
                        new ChoiceOptionSpec(ThreeMode.LOW.name(), "debug.dashboard.test.mode_three.low", null, null),
                        new ChoiceOptionSpec(ThreeMode.MEDIUM.name(), "debug.dashboard.test.mode_three.medium", null, null),
                        new ChoiceOptionSpec(ThreeMode.HIGH.name(), "debug.dashboard.test.mode_three.high", null, null)),
                        ChoicePresentation.AUTO))
                .register();

        settings.enumeration(MODE_FOUR, "debug.dashboard.test.mode_four", FourMode.class)
                .detail("debug.dashboard.test.mode_four.detail")
                .parent(GROUP)
                .defaultValue(FourMode.ALPHA)
                .choice(new ChoiceSpec(List.of(
                        new ChoiceOptionSpec(FourMode.ALPHA.name(), "debug.dashboard.test.mode_four.alpha", null, null),
                        new ChoiceOptionSpec(FourMode.BETA.name(), "debug.dashboard.test.mode_four.beta", null, null),
                        new ChoiceOptionSpec(FourMode.GAMMA.name(), "debug.dashboard.test.mode_four.gamma", null, null),
                        new ChoiceOptionSpec(FourMode.DELTA.name(), "debug.dashboard.test.mode_four.delta", null, null)),
                        ChoicePresentation.DROPDOWN))
                .register();

        settings.bool(MACRO_BOOL, "debug.dashboard.test.macro_bool")
                .detail("debug.dashboard.test.macro_bool.detail")
                .parent(GROUP)
                .impact(ChangeImpact.RECOMPILE_SHADERS)
                .defaultValue(false)
                .register();

        settings.floating(MACRO_VALUE, "debug.dashboard.test.macro_value")
                .detail("debug.dashboard.test.macro_value.detail")
                .parent(GROUP)
                .impact(ChangeImpact.RECOMPILE_SHADERS)
                .defaultValue(2.0f)
                .number(0.0f, 8.0f, 0.5f, "%.1f")
                .register();

        settings.enumeration(MACRO_CHOICE, "debug.dashboard.test.macro_choice", MacroChoiceMode.class)
                .detail("debug.dashboard.test.macro_choice.detail")
                .parent(GROUP)
                .impact(ChangeImpact.RECOMPILE_SHADERS)
                .defaultValue(MacroChoiceMode.LAB_PBR)
                .choice(new ChoiceSpec(List.of(
                        new ChoiceOptionSpec(MacroChoiceMode.LAB_PBR.name(), "debug.dashboard.test.macro_choice.labpbr", null, null),
                        new ChoiceOptionSpec(MacroChoiceMode.SEUS_PBR.name(), "debug.dashboard.test.macro_choice.seus", null, null),
                        new ChoiceOptionSpec(MacroChoiceMode.STANDARD_PBR.name(), "debug.dashboard.test.macro_choice.standard", null, null),
                        new ChoiceOptionSpec(MacroChoiceMode.VANILLA.name(), "debug.dashboard.test.macro_choice.vanilla", null, null)),
                        ChoicePresentation.AUTO))
                .register();

        macros.flag("SKETCH_TEST_BOOL")
                .setting(MACRO_BOOL)
                .displayKey("debug.dashboard.test.macro_bool")
                .detail("debug.dashboard.test.macro_bool.detail")
                .register();
        macros.value("SKETCH_TEST_SCALE")
                .setting(MACRO_VALUE)
                .displayKey("debug.dashboard.test.macro_value")
                .detail("debug.dashboard.test.macro_value.detail")
                .control(rogo.sketch.core.ui.control.ControlSpec.number(rogo.sketch.core.ui.control.NumericSpec.floating(0.0, 8.0, 0.5, "%.1f")))
                .register();
        macros.choice("SKETCH_TEST_PBR_MODE")
                .setting(MACRO_CHOICE)
                .displayKey("debug.dashboard.test.macro_choice")
                .detail("debug.dashboard.test.macro_choice.detail")
                .control(rogo.sketch.core.ui.control.ControlSpec.choice(
                        new ChoiceSpec(List.of(
                                new ChoiceOptionSpec(MacroChoiceMode.LAB_PBR.name(), "debug.dashboard.test.macro_choice.labpbr", null, null),
                                new ChoiceOptionSpec(MacroChoiceMode.SEUS_PBR.name(), "debug.dashboard.test.macro_choice.seus", null, null),
                                new ChoiceOptionSpec(MacroChoiceMode.STANDARD_PBR.name(), "debug.dashboard.test.macro_choice.standard", null, null),
                                new ChoiceOptionSpec(MacroChoiceMode.VANILLA.name(), "debug.dashboard.test.macro_choice.vanilla", null, null)),
                                ChoicePresentation.AUTO)))
                .option(MacroChoiceMode.LAB_PBR.name(), "SKETCH_TEST_PBR_LAB")
                .option(MacroChoiceMode.SEUS_PBR.name(), "SKETCH_TEST_PBR_SEUS")
                .option(MacroChoiceMode.STANDARD_PBR.name(), "SKETCH_TEST_PBR_STANDARD")
                .option(MacroChoiceMode.VANILLA.name(), "SKETCH_TEST_PBR_VANILLA")
                .register();
    }

    @Override
    public DashboardTestModuleRuntime createRuntime() {
        return new DashboardTestModuleRuntime();
    }
    public enum TwoMode {
        LEFT,
        RIGHT
    }

    public enum ThreeMode {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum FourMode {
        ALPHA,
        BETA,
        GAMMA,
        DELTA
    }

    public enum MacroChoiceMode {
        LAB_PBR,
        SEUS_PBR,
        STANDARD_PBR,
        VANILLA
    }
}
