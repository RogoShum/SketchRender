package rogo.sketch.config;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bridges persisted Forge config values into the core module setting runtime.
 */
public class ForgeSettingAdapter {
    private ModuleSettingRegistry registry;
    private final Map<KeyId, Binding<?>> bindings = new LinkedHashMap<>();

    public void attach(ModuleSettingRegistry registry) {
        this.registry = registry;
        syncAllToCore();
    }

    public void bindBoolean(KeyId settingId, Supplier<Boolean> source) {
        bindings.put(settingId, new Binding<>(settingId, source));
    }

    public void bindFloat(KeyId settingId, Supplier<Float> source) {
        bindings.put(settingId, new Binding<>(settingId, source));
    }

    public void bindInt(KeyId settingId, Supplier<Integer> source) {
        bindings.put(settingId, new Binding<>(settingId, source));
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
        if (registry != null && registry.hasSetting(settingId)) {
            registry.setValue(settingId, value);
        }
    }

    public @Nullable ModuleSettingRegistry registry() {
        return registry;
    }

    private record Binding<T>(
            KeyId settingId,
            Supplier<T> source
    ) {
    }
}
