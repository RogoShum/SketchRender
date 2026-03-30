package rogo.sketch.core.ui.control;

import org.jetbrains.annotations.Nullable;

public record ControlSpec(
        ControlKind kind,
        @Nullable NumericSpec numericSpec,
        @Nullable ChoiceSpec choiceSpec
) {
    public static ControlSpec toggle() {
        return new ControlSpec(ControlKind.TOGGLE, null, null);
    }

    public static ControlSpec number(NumericSpec numericSpec) {
        return new ControlSpec(ControlKind.NUMBER, numericSpec, null);
    }

    public static ControlSpec slider(NumericSpec numericSpec) {
        return new ControlSpec(ControlKind.SLIDER, numericSpec, null);
    }

    public static ControlSpec choice(ChoiceSpec choiceSpec) {
        return new ControlSpec(ControlKind.CHOICE, null, choiceSpec);
    }
}
