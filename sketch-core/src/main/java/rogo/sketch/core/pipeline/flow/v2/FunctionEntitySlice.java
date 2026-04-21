package rogo.sketch.core.pipeline.flow.v2;

import java.util.List;

public record FunctionEntitySlice(List<StageEntityView.Entry> entries) {
    public FunctionEntitySlice {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}
