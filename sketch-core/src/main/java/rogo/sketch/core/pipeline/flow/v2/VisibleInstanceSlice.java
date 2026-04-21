package rogo.sketch.core.pipeline.flow.v2;

import java.util.List;

public record VisibleInstanceSlice(
        GeometryBatchKey geometryBatchKey,
        List<StageEntityView.Entry> visibleEntries,
        long visibleRevision,
        long firstVisibleOrderKey
) {
    public VisibleInstanceSlice {
        visibleEntries = visibleEntries != null ? List.copyOf(visibleEntries) : List.of();
    }
}
