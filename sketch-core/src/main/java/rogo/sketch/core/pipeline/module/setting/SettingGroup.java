package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Non-value container for organizing settings into UI groups.
 */
public class SettingGroup extends SettingNode<Void> {
    public SettingGroup(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            boolean visibleInGui,
            List<DependencyRule> dependencies) {
        this(id, moduleId, displayKey, detailKey, parentId, visibleInGui, () -> true, dependencies);
    }

    public SettingGroup(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            boolean visibleInGui,
            BooleanSupplier visibilityRule,
            List<DependencyRule> dependencies) {
        this(id, moduleId, displayKey, null, detailKey, parentId, visibleInGui, visibilityRule, dependencies);
    }

    public SettingGroup(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            boolean visibleInGui,
            List<DependencyRule> dependencies) {
        this(id, moduleId, displayKey, summaryKey, detailKey, parentId, visibleInGui, () -> true, dependencies);
    }

    public SettingGroup(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            boolean visibleInGui,
            BooleanSupplier visibilityRule,
            List<DependencyRule> dependencies) {
        super(id, moduleId, displayKey, summaryKey, detailKey, parentId, ChangeImpact.RUNTIME_ONLY, visibleInGui, visibilityRule, dependencies, null, null);
    }

    @Override
    public boolean isGroup() {
        return true;
    }

    @Override
    public Class<Void> valueType() {
        return Void.class;
    }

    @Override
    public Void coerceValue(Object rawValue) {
        return null;
    }
}
