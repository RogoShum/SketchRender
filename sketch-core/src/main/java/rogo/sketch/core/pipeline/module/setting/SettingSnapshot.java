package rogo.sketch.core.pipeline.module.setting;

import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of visible setting values and active-state resolution.
 */
public class SettingSnapshot {
    private final Map<KeyId, Object> values;
    private final Set<KeyId> activeSettings;

    public SettingSnapshot(Map<KeyId, Object> values, Set<KeyId> activeSettings) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.activeSettings = Collections.unmodifiableSet(activeSettings);
    }

    @SuppressWarnings("unchecked")
    public <T> T value(KeyId keyId) {
        return (T) values.get(keyId);
    }

    public boolean isActive(KeyId keyId) {
        return activeSettings.contains(keyId);
    }

    public Map<KeyId, Object> values() {
        return values;
    }

    public Set<KeyId> activeSettings() {
        return activeSettings;
    }
}
