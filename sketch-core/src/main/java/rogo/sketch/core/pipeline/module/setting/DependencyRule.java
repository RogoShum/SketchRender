package rogo.sketch.core.pipeline.module.setting;

import rogo.sketch.core.util.KeyId;

/**
 * Declares a dependency that controls whether a setting is currently active.
 */
public record DependencyRule(
        KeyId targetSetting,
        DependencyType dependencyType
) {
    public static DependencyRule requiresEnabled(KeyId targetSetting) {
        return new DependencyRule(targetSetting, DependencyType.REQUIRES_ENABLED);
    }

    public static DependencyRule requiresTrue(KeyId targetSetting) {
        return new DependencyRule(targetSetting, DependencyType.REQUIRES_TRUE);
    }

    public enum DependencyType {
        REQUIRES_ENABLED,
        REQUIRES_TRUE
    }
}
