package rogo.sketch.core.pipeline.module.setting;

import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import rogo.sketch.core.util.KeyId;

import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of visible setting values and active-state resolution.
 */
public class SettingSnapshot {
    private final Object2ObjectOpenHashMap<KeyId, Object> values;
    private final ObjectOpenHashSet<KeyId> activeSettings;
    private final Map<KeyId, Object> valuesView;
    private final Set<KeyId> activeSettingsView;

    public SettingSnapshot(Map<KeyId, Object> values, Set<KeyId> activeSettings) {
        this.values = new Object2ObjectOpenHashMap<>(values);
        this.activeSettings = new ObjectOpenHashSet<>(activeSettings);
        this.values.trim();
        this.activeSettings.trim();
        this.valuesView = Object2ObjectMaps.unmodifiable(this.values);
        this.activeSettingsView = ObjectSets.unmodifiable(this.activeSettings);
    }

    @SuppressWarnings("unchecked")
    public <T> T value(KeyId keyId) {
        return (T) values.get(keyId);
    }

    public boolean isActive(KeyId keyId) {
        return activeSettings.contains(keyId);
    }

    public Map<KeyId, Object> values() {
        return valuesView;
    }

    public Set<KeyId> activeSettings() {
        return activeSettingsView;
    }
}
