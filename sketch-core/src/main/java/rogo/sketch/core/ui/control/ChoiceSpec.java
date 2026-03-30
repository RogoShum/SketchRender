package rogo.sketch.core.ui.control;

import java.util.List;

public record ChoiceSpec(
        List<ChoiceOptionSpec> options,
        ChoicePresentation presentation
) {
    public ChoiceSpec {
        options = List.copyOf(options);
    }
}
