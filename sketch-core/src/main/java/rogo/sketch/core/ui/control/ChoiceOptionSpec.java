package rogo.sketch.core.ui.control;

import org.jetbrains.annotations.Nullable;

public record ChoiceOptionSpec(
        String value,
        String displayKey,
        @Nullable String summaryKey,
        @Nullable String detailKey
) {
}
