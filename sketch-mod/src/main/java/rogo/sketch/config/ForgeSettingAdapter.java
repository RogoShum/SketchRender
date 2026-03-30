package rogo.sketch.config;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bridges persisted Forge config values into the core module setting runtime.
 */
public class ForgeSettingAdapter {
    private ModuleSettingRegistry registry;
    private final Map<KeyId, Binding<?>> bindings = new LinkedHashMap<>();
    private final Map<String, Binding<?>> bindingsByControlId = new LinkedHashMap<>();

    public void attach(ModuleSettingRegistry registry) {
        this.registry = registry;
        syncAllToCore();
    }

    public void bindBoolean(
            KeyId settingId,
            Supplier<Boolean> source,
            Consumer<Boolean> sink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>("setting/" + settingId, settingId, source, sink, enabled, disabledDetailKey));
    }

    public void bindFloat(
            KeyId settingId,
            Supplier<Float> source,
            Consumer<Float> sink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>("setting/" + settingId, settingId, source, sink, enabled, disabledDetailKey));
    }

    public void bindInt(
            KeyId settingId,
            Supplier<Integer> source,
            Consumer<Integer> sink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>("setting/" + settingId, settingId, source, sink, enabled, disabledDetailKey));
    }

    public <T> void bindExternal(
            String controlId,
            Supplier<T> source,
            Consumer<T> sink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey) {
        register(new Binding<>(controlId, null, source, sink, enabled, disabledDetailKey));
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
                registry.setValue(binding.settingId(), binding.source().get());
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

    public float getFloat(KeyId settingId, Supplier<Float> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Float value = registry.getValue(settingId);
        return value != null ? value : fallback.get();
    }

    public int getInt(KeyId settingId, Supplier<Integer> fallback) {
        if (registry == null) {
            return fallback.get();
        }
        Integer value = registry.getValue(settingId);
        return value != null ? value : fallback.get();
    }

    public void setValue(KeyId settingId, Object value) {
        Binding<?> binding = bindings.get(settingId);
        if (binding != null) {
            binding.writeUnchecked(value);
            syncValueToCore(settingId, binding.source().get());
            return;
        }
        syncValueToCore(settingId, value);
    }

    public void syncValueToCore(KeyId settingId, Object value) {
        if (registry != null && registry.hasSetting(settingId)) {
            registry.setValue(settingId, value);
        }
    }

    public @Nullable Object readControl(String controlId) {
        Binding<?> binding = bindingsByControlId.get(controlId);
        return binding != null ? binding.source().get() : null;
    }

    public void writeControl(String controlId, Object value) {
        Binding<?> binding = bindingsByControlId.get(controlId);
        if (binding != null) {
            binding.writeUnchecked(value);
            if (binding.settingId() != null) {
                syncValueToCore(binding.settingId(), binding.source().get());
            }
        }
    }

    public boolean isEnabled(String controlId) {
        Binding<?> binding = bindingsByControlId.get(controlId);
        return binding == null || binding.enabled().get();
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

    public record Binding<T>(
            String controlId,
            @Nullable KeyId settingId,
            Supplier<T> source,
            Consumer<T> sink,
            Supplier<Boolean> enabled,
            Supplier<@Nullable String> disabledDetailKey
    ) {
        @SuppressWarnings("unchecked")
        public void writeUnchecked(Object value) {
            sink.accept((T) value);
        }
    }
}
