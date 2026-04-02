package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.pipeline.information.InstanceInfo;

import java.util.List;

public record VisibleBatchSlice<I extends InstanceInfo<?>>(
        BatchBucket<I> bucket,
        List<I> visibleInstances,
        long visibleRevision,
        long firstVisibleOrderKey
) {
}
