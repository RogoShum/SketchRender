package rogo.sketch.core.shader.config;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.control.ControlSpec;

public record MacroSnapshotEntry(
        String name,
        String value,
        MacroContext.MacroLayer layer,
        @Nullable String sourceId,
        @Nullable String ownerId,
        MacroEntryType type,
        boolean editable,
        @Nullable String displayKey,
        @Nullable String summaryKey,
        @Nullable String detailKey,
        @Nullable ControlSpec controlSpec
) {
    public boolean flag() {
        return type == MacroEntryType.FLAG;
    }
}
