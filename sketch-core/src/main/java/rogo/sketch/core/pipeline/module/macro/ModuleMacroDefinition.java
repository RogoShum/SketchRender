package rogo.sketch.core.pipeline.module.macro;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

/**
 * Descriptor-owned macro metadata.
 */
public record ModuleMacroDefinition(
        String moduleId,
        String macroName,
        MacroKind kind,
        @Nullable KeyId settingId
) {
    public enum MacroKind {
        FLAG,
        VALUE
    }
}
