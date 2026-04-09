package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class FunctionExecutionOrderIndex {
    public List<FunctionInstanceStore.Entry> order(Collection<FunctionInstanceStore.Entry> sourceEntries) {
        List<FunctionInstanceStore.Entry> ordered = new ArrayList<>(sourceEntries);
        ordered.sort(Comparator
                .comparingInt((FunctionInstanceStore.Entry entry) -> DefaultBatchContainers.orderOf(entry.containerType()))
                .thenComparing(this::compareWithinContainer));
        return ordered;
    }

    private int compareWithinContainer(FunctionInstanceStore.Entry left, FunctionInstanceStore.Entry right) {
        if (DefaultBatchContainers.PRIORITY.equals(left.containerType())
                && DefaultBatchContainers.PRIORITY.equals(right.containerType())) {
            int comparableOrder = compareComparable(left.graphics(), right.graphics());
            if (comparableOrder != 0) {
                return comparableOrder;
            }
        }
        return Long.compare(left.orderHint(), right.orderHint());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private int compareComparable(Object left, Object right) {
        if (left == null || right == null) {
            return 0;
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            try {
                return comparable.compareTo(right);
            } catch (ClassCastException ignored) {
                return 0;
            }
        }
        return 0;
    }
}

