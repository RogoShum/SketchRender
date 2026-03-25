package rogo.sketch.core.pipeline.module.setting;

import rogo.sketch.core.util.KeyId;

public record SettingChangeEvent(
        String moduleId,
        KeyId settingId,
        Object oldValue,
        Object newValue,
        ChangeImpact changeImpact
) {
}
