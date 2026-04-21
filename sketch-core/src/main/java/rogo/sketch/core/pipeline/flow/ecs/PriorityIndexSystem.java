package rogo.sketch.core.pipeline.flow.ecs;

import rogo.sketch.core.pipeline.flow.v2.StageEntityView;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PriorityIndexSystem {
    public List<StageEntityView.Entry> order(List<StageEntityView.Entry> entries) {
        if (entries == null || entries.size() <= 1) {
            return entries != null ? entries : List.of();
        }
        entries.sort(Comparator.comparing(this::sortKey, this::compareSortKey)
                .thenComparingLong(StageEntityView.Entry::orderHint));
        return entries;
    }

    private Object sortKey(StageEntityView.Entry entry) {
        return entry.sortKey();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private int compareSortKey(Object left, Object right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        if (left instanceof Comparable comparable && right != null && left.getClass().isInstance(right)) {
            try {
                return comparable.compareTo(right);
            } catch (ClassCastException ignored) {
                return 0;
            }
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
