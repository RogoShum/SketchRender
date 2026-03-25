package rogo.sketch.core.pipeline.module.metric;

import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MetricSnapshot {
    private final Map<KeyId, Object> values;

    public MetricSnapshot(Map<KeyId, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @SuppressWarnings("unchecked")
    public <T> T value(KeyId keyId) {
        return (T) values.get(keyId);
    }

    public Map<KeyId, Object> values() {
        return values;
    }
}
