package rogo.sketch.core.pipeline.module.descriptor;

import rogo.sketch.core.pipeline.module.macro.ModuleMacroDefinition;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.module.setting.SettingNode;
import rogo.sketch.core.util.KeyId;

public interface ModuleDescriptorContext {
    String moduleId();

    KeyId moduleEnabledSettingId();

    ModuleSettingRegistry settings();

    void registerSetting(SettingNode<?> setting);

    void registerMacro(ModuleMacroDefinition definition);

    void registerMetricDescriptor(MetricDescriptor descriptor);
}
