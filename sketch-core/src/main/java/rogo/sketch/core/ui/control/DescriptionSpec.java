package rogo.sketch.core.ui.control;

import org.jetbrains.annotations.Nullable;

public record DescriptionSpec(
        String displayKey,
        @Nullable String summaryKey,
        @Nullable String detailKey
) {
}
