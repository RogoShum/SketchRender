package rogo.sketch.config;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PropertySettingAdapter {
    private ModuleSettingRegistry registry;
    private final Map<KeyId, Binding<?>> bindings = new LinkedHashMap<>();
    private final Map<String, Binding<?>> bindingsByControlId = new LinkedHashMap<>();

    public void attach(ModuleSettingRegistry registry) {
        this.registry = registry;
        syncAllToCore();
    }

    public void bindBoolean(
            KeyId settingId,
            Supplier<Boolean> persistedSource,
            Consumer<Boolean> persistedSink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>("setting/" + settingId, settingId, persistedSource, persistedSink, enabled, disabledDetailKey));
    }

    public void bindFloat(
            KeyId settingId,
            Supplier<Float> persistedSource,
            Consumer<Float> persistedSink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>("setting/" + settingId, settingId, persistedSource, persistedSink, enabled, disabledDetailKey));
    }

    public void bindInt(
            KeyId settingId,
            Supplier<Integer> persistedSource,
            Consumer<Integer> persistedSink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>("setting/" + settingId, settingId, persistedSource, persistedSink, enabled, disabledDetailKey));
    }

    public <T> void bindExternal(
            String controlId,
            Supplier<T> persistedSource,
            Consumer<T> persistedSink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>(controlId, null, persistedSource, persistedSink, enabled, disabledDetailKey));
    }

    private void register(Binding<?> binding) {
        if (binding.settingId() != null) {
            bindings.put(binding.settingId(), binding);
        }
        bindingsByControlId.put(binding.controlId(), binding);
    }

    public void syncAllToCore() {
        if (registry == null) {
            return;
        }
        for (Binding<?> binding : bindings.values()) {
            if (registry.hasSetting(binding.settingId())) {
                registry.setValue(binding.settingId(), binding.readPersisted());
            }
        }
    }

    public boolean getBoolean(KeyId settingId, Supplier<Boolean> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Boolean value = registry.getValue(settingId);
        return value != null ? value : fallback.get();
    }

    public boolean getPreviewBoolean(KeyId settingId, Supplier<Boolean> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Boolean value = registry.getPreviewValue(settingId);
        return value != null ? value : fallback.get();
    }

    public float getFloat(KeyId settingId, Supplier<Float> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Float value = registry.getValue(settingId);
        return value != null ? value : fallback.get();
    }

    public float getPreviewFloat(KeyId settingId, Supplier<Float> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Float value = registry.getPreviewValue(settingId);
        return value != null ? value : fallback.get();
    }

    public int getInt(KeyId settingId, Supplier<Integer> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Integer value = registry.getValue(settingId);
        return value != null ? value : fallback.get();
    }

    public int getPreviewInt(KeyId settingId, Supplier<Integer> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Integer value = registry.getPreviewValue(settingId);
        return value != null ? value : fallback.get();
    }

    public void setValue(KeyId settingId, Object value) {
        Binding<?> binding = bindings.get(settingId);
        if (binding != null) {
            binding.writeUnchecked(value);
            syncValueToCore(settingId, binding.readPersisted());
            return;
        }
        syncValueToCore(settingId, value);
    }

    public void syncValueToCore(KeyId settingId, Object value) {
        if (registry != null && registry.hasSetting(settingId)) {
            registry.queueValue(settingId, value);
        }
    }

    public @Nullable Object readControl(String controlId) {
        Binding<?> binding = bindingsByControlId.get(controlId);
        if (binding == null) {
            return null;
        }
        if (binding.settingId() != null && registry != null && registry.hasSetting(binding.settingId())) {
            return registry.getPreviewValue(binding.settingId());
        }
        return binding.readPersisted();
    }

    public void writeControl(String controlId, Object value) {
        Binding<?> binding = bindingsByControlId.get(controlId);
        if (binding == null) {
            return;
        }
        binding.writeUnchecked(value);
        if (binding.settingId() != null) {
            syncValueToCore(binding.settingId(), binding.readPersisted());
        }
    }

    public boolean isEnabled(String controlId) {
        Binding<?> binding = bindingsByControlId.get(controlId);
        if (binding == null) {
            return true;
        }
        if (!binding.enabled().get()) {
            return false;
        }
        return binding.settingId() == null || registry == null || !registry.hasSetting(binding.settingId())
                || registry.isPreviewActive(binding.settingId());
    }

    public @Nullable String disabledDetailKey(String controlId) {
        Binding<?> binding = bindingsByControlId.get(controlId);
        if (binding == null || binding.enabled().get()) {
            return null;
        }
        return binding.disabledDetailKey().get();
    }

    public boolean hasControl(String controlId) {
        return bindingsByControlId.containsKey(controlId);
    }

    public @Nullable ModuleSettingRegistry registry() {
        return registry;
    }

    public boolean hasBinding(KeyId settingId) {
        return settingId != null && bindings.containsKey(settingId);
    }

    public record Binding<T>(
            String controlId,
            @Nullable KeyId settingId,
            Supplier<T> persistedSource,
            Consumer<T> persistedSink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey
    ) {
        public T readPersisted() {
            return persistedSource.get();
        }

        @SuppressWarnings("unchecked")
        public void writeUnchecked(Object value) {
            persistedSink.accept((T) value);
        }
    }
}
