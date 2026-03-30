package rogo.sketch.gui.debugui;

import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.Config;
import rogo.sketch.core.debugger.DashboardControlAccessor;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.module.setting.SettingNode;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.vanilla.module.DashboardTestModuleDescriptor;

public class AdaptiveDashboardControlAccessor implements DashboardControlAccessor {
    @Override
    public @Nullable Object readValue(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            return Config.coreSettings().readControl(controlId);
        }
        if (isDashboardTestControl(controlId)) {
            return testControlValue(controlId);
        }
        if (setting != null) {
            return registry.getValue(setting.id());
        }
        if (controlId.startsWith("setting/")) {
            return registry.getValue(KeyId.of(controlId.substring("setting/".length())));
        }
        return null;
    }

    @Override
    public void writeValue(String controlId, Object value, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            Config.coreSettings().writeControl(controlId, value);
            return;
        }
        if (isDashboardTestControl(controlId)) {
            if (setting != null) {
                registry.setValue(setting.id(), value);
            }
            if (controlId.equals("setting/" + DashboardTestModuleDescriptor.CHECK_CULL)) {
                CullingStateManager.CHECKING_CULL = Boolean.TRUE.equals(value);
            } else if (controlId.equals("setting/" + DashboardTestModuleDescriptor.CHECK_TEXTURE)) {
                CullingStateManager.CHECKING_TEXTURE = Boolean.TRUE.equals(value);
            }
            return;
        }
        if (setting != null) {
            registry.setValue(setting.id(), value);
            return;
        }
        if (controlId.startsWith("setting/")) {
            registry.setValue(KeyId.of(controlId.substring("setting/".length())), value);
        }
    }

    @Override
    public boolean isEnabled(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            return Config.coreSettings().isEnabled(controlId);
        }
        if (isDashboardTestControl(controlId)) {
            return !FMLEnvironment.production;
        }
        if (setting != null) {
            return registry.isActive(setting.id());
        }
        if (controlId.startsWith("setting/")) {
            return registry.isActive(KeyId.of(controlId.substring("setting/".length())));
        }
        return true;
    }

    @Override
    public @Nullable String disabledDetailKey(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting) {
        if (Config.coreSettings().hasControl(controlId)) {
            return Config.coreSettings().disabledDetailKey(controlId);
        }
        if (isDashboardTestControl(controlId) && FMLEnvironment.production) {
            return "sketch_render.detail.test_feature";
        }
        return null;
    }

    private boolean isDashboardTestControl(String controlId) {
        return controlId.equals("setting/" + DashboardTestModuleDescriptor.CHECK_CULL)
                || controlId.equals("setting/" + DashboardTestModuleDescriptor.CHECK_TEXTURE);
    }

    private Object testControlValue(String controlId) {
        if (controlId.equals("setting/" + DashboardTestModuleDescriptor.CHECK_CULL)) {
            return CullingStateManager.CHECKING_CULL;
        }
        if (controlId.equals("setting/" + DashboardTestModuleDescriptor.CHECK_TEXTURE)) {
            return CullingStateManager.CHECKING_TEXTURE;
        }
        return null;
    }
}
