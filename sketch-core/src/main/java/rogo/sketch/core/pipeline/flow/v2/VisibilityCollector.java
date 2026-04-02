package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.information.InstanceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy collector that turns {@link BatchContainer} visibility results into
 * {@link VisibleBatchSlice} snapshots.
 * <p>
 * The V2 raster/translucent main path now collects visibility through
 * {@link VisibilityIndex} implementations directly.
 * </p>
 */
@Deprecated(forRemoval = false)
public final class VisibilityCollector<I extends InstanceInfo<?>> {
    public void prepare(BatchContainer<?, I> container, RenderContext context) {
        if (container != null) {
            container.prepareVisibility(context);
        }
    }

    public List<VisibleBatchSlice<I>> collect(BatchContainer<?, I> container) {
        List<VisibleBatchSlice<I>> slices = new ArrayList<>();
        if (container == null) {
            return slices;
        }
        for (RenderBatch<I> batch : container.getActiveBatches()) {
            slices.add(new VisibleBatchSlice<>(
                    BatchBucket.from(batch),
                    List.copyOf(batch.getVisibleInstances()),
                    batch.getVisibleRevision(),
                    batch.getFirstVisibleOrderKey()));
        }
        return slices;
    }
}
