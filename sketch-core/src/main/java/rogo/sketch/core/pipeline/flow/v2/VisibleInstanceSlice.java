package rogo.sketch.core.pipeline.flow.v2;

import java.util.List;

public record VisibleInstanceSlice(
        GeometryBatchKey geometryBatchKey,
        List<InstanceHandle> visibleHandles,
        long visibleRevision,
        long firstVisibleOrderKey
) {
    public VisibleInstanceSlice {
        visibleHandles = visibleHandles != null ? List.copyOf(visibleHandles) : List.of();
    }
}
