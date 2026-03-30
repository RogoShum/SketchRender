package rogo.sketch.core.shader.config;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.control.ControlSpec;

public record MacroEntryDescriptor(
        String name,
        MacroEntryType type,
        boolean editable,
        @Nullable String value,
        @Nullable String displayKey,
        @Nullable String summaryKey,
        @Nullable String detailKey,
        @Nullable ControlSpec controlSpec
) {
    public static MacroEntryDescriptor constantFlag(String name) {
        return new MacroEntryDescriptor(name, MacroEntryType.FLAG, false, "1", null, null, null, null);
    }

    public static MacroEntryDescriptor constantValue(String name, String value) {
        return new MacroEntryDescriptor(name, MacroEntryType.VALUE, false, value, null, null, null, null);
    }
}
