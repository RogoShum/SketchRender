package rogo.sketch.gui.debugui;

import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.Config;
import rogo.sketch.core.debugger.DashboardControlAccessor;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.module.setting.SettingNode;
import rogo.sketch.core.util.KeyId;

public class AdaptiveDashboardControlAccessor implements DashboardControlAccessor {
    @Override
    public @Nullable Object readValue(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            return Config.coreSettings().readControl(controlId);
        }
        if (setting != null) {
            return registry.getPreviewValue(setting.id());
        }
        if (controlId.startsWith("setting/")) {
            return registry.getPreviewValue(KeyId.of(controlId.substring("setting/".length())));
        }
        return null;
    }

    @Override
    public void writeValue(String controlId, Object value, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            Config.coreSettings().writeControl(controlId, value);
            return;
        }
        if (setting != null) {
            registry.queueValue(setting.id(), value);
            return;
        }
        if (controlId.startsWith("setting/")) {
            registry.queueValue(KeyId.of(controlId.substring("setting/".length())), value);
        }
    }

    @Override
    public boolean isEnabled(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            return Config.coreSettings().isEnabled(controlId);
        }
        if (setting != null) {
            return registry.isPreviewActive(setting.id());
        }
        if (controlId.startsWith("setting/")) {
            return registry.isPreviewActive(KeyId.of(controlId.substring("setting/".length())));
        }
        return true;
    }

    @Override
    public @Nullable String disabledDetailKey(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            return Config.coreSettings().disabledDetailKey(controlId);
        }
        return null;
    }
}
