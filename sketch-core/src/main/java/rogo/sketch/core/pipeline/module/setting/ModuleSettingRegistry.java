package rogo.sketch.core.pipeline.module.setting;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Runtime-owned setting registry used by module runtimes and UI adapters.
 */
public class ModuleSettingRegistry {
    private final Object2ObjectOpenHashMap<KeyId, SettingNode<?>> settings = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<KeyId, Object> values = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<KeyId, Object> pendingValues = new Object2ObjectOpenHashMap<>();
    private final Object2BooleanOpenHashMap activeSettings = new Object2BooleanOpenHashMap();
    private final Object2BooleanOpenHashMap previewActiveSettings = new Object2BooleanOpenHashMap();
    private final ObjectArrayList<SettingNode<?>> orderedSettings = new ObjectArrayList<>();
    private final List<Consumer<SettingChangeEvent>> listeners = new CopyOnWriteArrayList<>();
    private Collection<SettingNode<?>> allSettingsView = List.of();
    private SettingSnapshot cachedSnapshot;

    public ModuleSettingRegistry() {
        activeSettings.defaultReturnValue(false);
        previewActiveSettings.defaultReturnValue(false);
    }

    public void registerSetting(SettingNode<?> setting) {
        Objects.requireNonNull(setting, "setting");
        if (settings.containsKey(setting.id())) {
            throw new IllegalStateException("Duplicate setting id: " + setting.id());
        }
        settings.put(setting.id(), setting);
        if (!setting.isGroup()) {
            values.put(setting.id(), setting.defaultValue());
        }
        rebuildSettingOrder();
        recomputeCommittedActiveMap();
        recomputePreviewActiveMap();
        invalidateSnapshot();
    }

    public Collection<SettingNode<?>> allSettings() {
        return allSettingsView;
    }

    public boolean hasSetting(KeyId settingId) {
        return settings.containsKey(settingId);
    }

    public List<SettingNode<?>> settingsForModule(String moduleId) {
        List<SettingNode<?>> result = new ArrayList<>();
        for (int i = 0; i < orderedSettings.size(); i++) {
            SettingNode<?> setting = orderedSettings.get(i);
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
        recomputeCommittedActiveMap();
        recomputePreviewActiveMap();
        invalidateSnapshot();
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
            recomputePreviewActiveMap();
            return;
        }
        pendingValues.put(settingId, newValue);
        recomputePreviewActiveMap();
    }

    public List<SettingChangeEvent> flushPendingChanges() {
        if (pendingValues.isEmpty()) {
            return List.of();
        }

        List<SettingChangeEvent> events = new ArrayList<>();
        for (int i = 0; i < orderedSettings.size(); i++) {
            SettingNode<?> setting = orderedSettings.get(i);
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
        recomputeCommittedActiveMap();
        recomputePreviewActiveMap();
        invalidateSnapshot();

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
        return settingId != null && activeSettings.getBoolean(settingId);
    }

    public boolean isPreviewActive(KeyId settingId) {
        return settingId != null && previewActiveSettings.getBoolean(settingId);
    }

    private boolean isActive(SettingNode<?> setting, ObjectOpenHashSet<KeyId> visited, Map<KeyId, Object> valueView) {
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
        SettingSnapshot snapshot = cachedSnapshot;
        if (snapshot != null) {
            return snapshot;
        }
        ObjectOpenHashSet<KeyId> active = new ObjectOpenHashSet<>();
        for (int i = 0; i < orderedSettings.size(); i++) {
            KeyId settingId = orderedSettings.get(i).id();
            if (activeSettings.getBoolean(settingId)) {
                active.add(settingId);
            }
        }
        cachedSnapshot = new SettingSnapshot(values, active);
        return cachedSnapshot;
    }

    private void rebuildSettingOrder() {
        orderedSettings.clear();
        ObjectOpenHashSet<KeyId> visited = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<KeyId> visiting = new ObjectOpenHashSet<>();
        for (SettingNode<?> setting : settings.values()) {
            visitForOrder(setting, visited, visiting);
        }
        allSettingsView = Collections.unmodifiableList(new ArrayList<>(orderedSettings));
    }

    private void visitForOrder(SettingNode<?> setting, ObjectOpenHashSet<KeyId> visited, ObjectOpenHashSet<KeyId> visiting) {
        if (setting == null || visited.contains(setting.id())) {
            return;
        }
        if (!visiting.add(setting.id())) {
            return;
        }
        if (setting.parentId() != null) {
            visitForOrder(settings.get(setting.parentId()), visited, visiting);
        }
        List<DependencyRule> dependencies = setting.dependencies();
        for (int i = 0; i < dependencies.size(); i++) {
            visitForOrder(settings.get(dependencies.get(i).targetSetting()), visited, visiting);
        }
        visiting.remove(setting.id());
        if (visited.add(setting.id())) {
            orderedSettings.add(setting);
        }
    }

    private void recomputeCommittedActiveMap() {
        recomputeActiveMap(activeSettings, values, false);
    }

    private void recomputePreviewActiveMap() {
        recomputeActiveMap(previewActiveSettings, values, true);
    }

    private void recomputeActiveMap(
            Object2BooleanOpenHashMap targetMap,
            Map<KeyId, Object> committedValues,
            boolean preview) {
        targetMap.clear();
        for (int i = 0; i < orderedSettings.size(); i++) {
            SettingNode<?> setting = orderedSettings.get(i);
            boolean active = isActive(setting, new ObjectOpenHashSet<>(), new ValueView(committedValues, preview));
            targetMap.put(setting.id(), active);
        }
    }

    private void invalidateSnapshot() {
        cachedSnapshot = null;
    }

    private final class ValueView implements Map<KeyId, Object> {
        private final Map<KeyId, Object> committedValues;
        private final boolean preview;

        private ValueView(Map<KeyId, Object> committedValues, boolean preview) {
            this.committedValues = committedValues;
            this.preview = preview;
        }

        @Override
        public int size() {
            return committedValues.size();
        }

        @Override
        public boolean isEmpty() {
            return committedValues.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return committedValues.containsKey(key) || (preview && pendingValues.containsKey(key));
        }

        @Override
        public boolean containsValue(Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(Object key) {
            if (!(key instanceof KeyId keyId)) {
                return null;
            }
            return preview && pendingValues.containsKey(keyId) ? pendingValues.get(keyId) : committedValues.get(keyId);
        }

        @Override
        public Object put(KeyId key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object remove(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map<? extends KeyId, ?> m) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Set<KeyId> keySet() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Collection<Object> values() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Set<Entry<KeyId, Object>> entrySet() {
            throw new UnsupportedOperationException();
        }
    }
}
