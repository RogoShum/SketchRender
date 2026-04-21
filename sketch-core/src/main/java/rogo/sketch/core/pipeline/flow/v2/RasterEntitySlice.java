package rogo.sketch.core.pipeline.flow.v2;

import java.util.List;

public record RasterEntitySlice(List<StageEntityView.Entry> entries) {
    public RasterEntitySlice {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}
