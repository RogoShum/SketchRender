package rogo.sketch.core.debugger;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.module.setting.SettingNode;

public interface DashboardControlAccessor {
    @Nullable Object readValue(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting);

    void writeValue(String controlId, Object value, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting);

    boolean isEnabled(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting);

    @Nullable String disabledDetailKey(String controlId, ModuleSettingRegistry registry, @Nullable SettingNode<?> setting);
}
