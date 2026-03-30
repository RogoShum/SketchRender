package rogo.sketch.core.pipeline.module.metric;

import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runtime metric registry. Modules expose descriptors and suppliers, while UI
 * layers consume snapshots.
 */
public class ModuleMetricRegistry {
    private final Map<KeyId, MetricDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<KeyId, Supplier<Object>> valueSuppliers = new ConcurrentHashMap<>();
    private final Map<String, Set<KeyId>> ownerMetrics = new ConcurrentHashMap<>();

    public void registerMetric(String ownerId, MetricDescriptor descriptor, Supplier<Object> supplier) {
        descriptors.put(descriptor.id(), descriptor);
        valueSuppliers.put(descriptor.id(), supplier);
        ownerMetrics.computeIfAbsent(ownerId, key -> ConcurrentHashMap.newKeySet()).add(descriptor.id());
    }

    public void unregisterOwner(String ownerId) {
        Set<KeyId> ids = ownerMetrics.remove(ownerId);
        if (ids == null) {
            return;
        }
        for (KeyId id : ids) {
            descriptors.remove(id);
            valueSuppliers.remove(id);
        }
    }

    public Collection<MetricDescriptor> descriptors() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    public MetricSnapshot snapshot() {
        Map<KeyId, Object> values = new LinkedHashMap<>();
        for (KeyId id : descriptors.keySet()) {
            Supplier<Object> supplier = valueSuppliers.get(id);
            if (supplier == null) {
                continue;
            }
            try {
                values.put(id, supplier.get());
            } catch (Exception e) {
                SketchDiagnostics.get().warn("metrics", "Failed to sample metric " + id, e);
            }
        }
        return new MetricSnapshot(values);
    }
}
