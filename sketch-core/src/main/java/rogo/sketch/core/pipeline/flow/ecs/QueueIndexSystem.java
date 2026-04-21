package rogo.sketch.core.pipeline.flow.ecs;

import rogo.sketch.core.pipeline.flow.v2.StageEntityView;

import java.util.Comparator;
import java.util.List;

public final class QueueIndexSystem {
    public List<StageEntityView.Entry> order(List<StageEntityView.Entry> entries) {
        if (entries == null || entries.size() <= 1) {
            return entries != null ? entries : List.of();
        }
        entries.sort(Comparator.comparingLong(StageEntityView.Entry::orderHint));
        return entries;
    }
}
