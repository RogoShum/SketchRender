package rogo.sketch.core.pipeline.module.setting;

import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Runtime-owned setting registry used by module runtimes and UI adapters.
 */
public class ModuleSettingRegistry {
    private final Map<KeyId, SettingNode<?>> settings = new LinkedHashMap<>();
    private final Map<KeyId, Object> values = new LinkedHashMap<>();
    private final Map<KeyId, Object> pendingValues = new LinkedHashMap<>();
    private final List<Consumer<SettingChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    public void registerSetting(SettingNode<?> setting) {
        Objects.requireNonNull(setting, "setting");
        if (settings.containsKey(setting.id())) {
            throw new IllegalStateException("Duplicate setting id: " + setting.id());
        }
        settings.put(setting.id(), setting);
        if (!setting.isGroup()) {
            values.put(setting.id(), setting.defaultValue());
        }
    }

    public Collection<SettingNode<?>> allSettings() {
        return Collections.unmodifiableCollection(settings.values());
    }

    public boolean hasSetting(KeyId settingId) {
        return settings.containsKey(settingId);
    }

    public List<SettingNode<?>> settingsForModule(String moduleId) {
        List<SettingNode<?>> result = new ArrayList<>();
        for (SettingNode<?> setting : settings.values()) {
            if (setting.moduleId().equals(moduleId)) {
                result.add(setting);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(KeyId settingId) {
        return (T) values.get(settingId);
    }

    @SuppressWarnings("unchecked")
    public <T> T getPreviewValue(KeyId settingId) {
        return (T) (pendingValues.containsKey(settingId) ? pendingValues.get(settingId) : values.get(settingId));
    }

    public boolean getBoolean(KeyId settingId, boolean fallback) {
        Object value = values.get(settingId);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public boolean hasPendingChanges() {
        return !pendingValues.isEmpty();
    }

    public void setValue(KeyId settingId, Object rawValue) {
        applyImmediateValue(settingId, rawValue);
    }

    public SettingChangeEvent applyImmediateValue(KeyId settingId, Object rawValue) {
        SettingNode<?> setting = settings.get(settingId);
        if (setting == null) {
            throw new IllegalArgumentException("Unknown setting id: " + settingId);
        }
        if (setting.isGroup()) {
            return null;
        }

        Object oldValue = values.get(settingId);
        Object newValue = setting.coerceValue(rawValue);
        pendingValues.remove(settingId);
        if (Objects.equals(oldValue, newValue)) {
            return null;
        }

        values.put(settingId, newValue);
        SettingChangeEvent event = new SettingChangeEvent(
                setting.moduleId(),
                settingId,
                oldValue,
                newValue,
                setting.changeImpact());
        for (Consumer<SettingChangeEvent> listener : listeners) {
            listener.accept(event);
        }
        return event;
    }

    public void queueValue(KeyId settingId, Object rawValue) {
        SettingNode<?> setting = settings.get(settingId);
        if (setting == null) {
            throw new IllegalArgumentException("Unknown setting id: " + settingId);
        }
        if (setting.isGroup()) {
            return;
        }

        Object newValue = setting.coerceValue(rawValue);
        Object committedValue = values.get(settingId);
        if (Objects.equals(committedValue, newValue)) {
            pendingValues.remove(settingId);
            return;
        }
        pendingValues.put(settingId, newValue);
    }

    public List<SettingChangeEvent> flushPendingChanges() {
        if (pendingValues.isEmpty()) {
            return List.of();
        }

        List<SettingChangeEvent> events = new ArrayList<>();
        for (SettingNode<?> setting : settings.values()) {
            if (setting.isGroup() || !pendingValues.containsKey(setting.id())) {
                continue;
            }

            Object oldValue = values.get(setting.id());
            Object newValue = setting.coerceValue(pendingValues.get(setting.id()));
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }

            values.put(setting.id(), newValue);
            events.add(new SettingChangeEvent(
                    setting.moduleId(),
                    setting.id(),
                    oldValue,
                    newValue,
                    setting.changeImpact()));
        }
        pendingValues.clear();

        for (SettingChangeEvent event : events) {
            for (Consumer<SettingChangeEvent> listener : listeners) {
                listener.accept(event);
            }
        }
        return List.copyOf(events);
    }

    public void addListener(Consumer<SettingChangeEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<SettingChangeEvent> listener) {
        listeners.remove(listener);
    }

    public boolean isActive(KeyId settingId) {
        SettingNode<?> setting = settings.get(settingId);
        if (setting == null) {
            return false;
        }
        return isActive(setting, new HashSet<>(), values);
    }

    public boolean isPreviewActive(KeyId settingId) {
        SettingNode<?> setting = settings.get(settingId);
        if (setting == null) {
            return false;
        }
        return isActive(setting, new HashSet<>(), new PreviewValuesMapView());
    }

    private boolean isActive(SettingNode<?> setting, Set<KeyId> visited, Map<KeyId, Object> valueView) {
        if (!visited.add(setting.id())) {
            return true;
        }

        if (setting.parentId() != null) {
            SettingNode<?> parent = settings.get(setting.parentId());
            if (parent != null) {
                if (!isActive(parent, visited, valueView)) {
                    return false;
                }
                if (!parent.isGroup()) {
                    Object parentValue = valueView.get(parent.id());
                    if (parentValue instanceof Boolean bool && !bool) {
                        return false;
                    }
                }
            }
        }

        for (DependencyRule dependency : setting.dependencies()) {
            SettingNode<?> target = settings.get(dependency.targetSetting());
            if (target == null) {
                continue;
            }
            if (!isActive(target, visited, valueView)) {
                return false;
            }
            if (dependency.dependencyType() == DependencyRule.DependencyType.REQUIRES_TRUE) {
                Object dependencyValue = valueView.get(target.id());
                if (!(dependencyValue instanceof Boolean bool) || !bool) {
                    return false;
                }
            }
        }
        return true;
    }

    public SettingSnapshot snapshot() {
        Set<KeyId> active = new LinkedHashSet<>();
        for (SettingNode<?> setting : settings.values()) {
            if (isActive(setting.id())) {
                active.add(setting.id());
            }
        }
        return new SettingSnapshot(values, active);
    }

    private final class PreviewValuesMapView extends AbstractMap<KeyId, Object> {
        @Override
        public Object get(Object key) {
            if (!(key instanceof KeyId keyId)) {
                return null;
            }
            return pendingValues.containsKey(keyId) ? pendingValues.get(keyId) : values.get(keyId);
        }

        @Override
        public Set<Entry<KeyId, Object>> entrySet() {
            LinkedHashMap<KeyId, Object> merged = new LinkedHashMap<>(values);
            merged.putAll(pendingValues);
            return Collections.unmodifiableSet(merged.entrySet());
        }
    }
}
