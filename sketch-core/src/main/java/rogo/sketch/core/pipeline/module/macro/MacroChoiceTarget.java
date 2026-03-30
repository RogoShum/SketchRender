package rogo.sketch.core.pipeline.module.macro;

import org.jetbrains.annotations.Nullable;

public record MacroChoiceTarget(
        String optionValue,
        String macroName,
        @Nullable String macroValue
) {
    public boolean flag() {
        return macroValue == null;
    }
}
