package rogo.sketch.core.pipeline.flow.v2;

import java.util.List;

public record ComputeEntitySlice(List<StageEntityView.Entry> entries) {
    public ComputeEntitySlice {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}
