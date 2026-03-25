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
        for (Map.Entry<KeyId, Supplier<Object>> entry : valueSuppliers.entrySet()) {
            try {
                values.put(entry.getKey(), entry.getValue().get());
            } catch (Exception e) {
                SketchDiagnostics.get().warn("metrics", "Failed to sample metric " + entry.getKey(), e);
            }
        }
        return new MetricSnapshot(values);
    }
}
