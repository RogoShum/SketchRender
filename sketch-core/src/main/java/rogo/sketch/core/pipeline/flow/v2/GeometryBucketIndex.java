package rogo.sketch.core.pipeline.flow.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class GeometryBucketIndex {
    private final Map<GeometryBatchKey, LinkedHashSet<InstanceHandle>> buckets = new LinkedHashMap<>();
    private final Map<InstanceHandle, GeometryBatchKey> membership = new LinkedHashMap<>();

    public void assign(InstanceHandle handle, GeometryBatchKey geometryBatchKey) {
        if (handle == null || !handle.isValid() || geometryBatchKey == null) {
            return;
        }
        GeometryBatchKey previous = membership.put(handle, geometryBatchKey);
        if (geometryBatchKey.equals(previous)) {
            return;
        }
        if (previous != null) {
            LinkedHashSet<InstanceHandle> previousHandles = buckets.get(previous);
            if (previousHandles != null) {
                previousHandles.remove(handle);
                if (previousHandles.isEmpty()) {
                    buckets.remove(previous);
                }
            }
        }
        buckets.computeIfAbsent(geometryBatchKey, ignored -> new LinkedHashSet<>()).add(handle);
    }

    public void remove(InstanceHandle handle) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        GeometryBatchKey existing = membership.remove(handle);
        if (existing == null) {
            return;
        }
        LinkedHashSet<InstanceHandle> handles = buckets.get(existing);
        if (handles != null) {
            handles.remove(handle);
            if (handles.isEmpty()) {
                buckets.remove(existing);
            }
        }
    }

    public GeometryBatchKey geometryBucketOf(InstanceHandle handle) {
        return handle == null ? null : membership.get(handle);
    }

    public List<InstanceHandle> handles(GeometryBatchKey geometryBatchKey) {
        LinkedHashSet<InstanceHandle> handles = buckets.get(geometryBatchKey);
        return handles != null ? List.copyOf(handles) : List.of();
    }

    public Map<GeometryBatchKey, List<InstanceHandle>> snapshot() {
        Map<GeometryBatchKey, List<InstanceHandle>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<GeometryBatchKey, LinkedHashSet<InstanceHandle>> entry : buckets.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    public boolean isEmpty() {
        return membership.isEmpty();
    }

    public int size() {
        return membership.size();
    }

    public void clear() {
        buckets.clear();
        membership.clear();
    }
}
