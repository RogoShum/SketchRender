package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.information.InstanceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy bucket adapter over {@link BatchContainer} active batches.
 * <p>
 * The V2 raster/translucent main path uses {@link GeometryBucketIndex}
 * instead; this wrapper is kept only for compatibility code that still reads
 * legacy {@link RenderBatch} collections.
 * </p>
 */
@Deprecated(forRemoval = false)
public final class BatchBucketIndex<I extends InstanceInfo<?>> {
    public List<BatchBucket<I>> activeBuckets(BatchContainer<?, I> container) {
        List<BatchBucket<I>> buckets = new ArrayList<>();
        if (container == null) {
            return buckets;
        }
        for (RenderBatch<I> batch : container.getActiveBatches()) {
            buckets.add(BatchBucket.from(batch));
        }
        return buckets;
    }
}
