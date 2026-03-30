package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.ui.control.ControlSpec;

import java.util.List;

public class BooleanSetting extends SettingNode<Boolean> {
    public BooleanSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            boolean defaultValue) {
        this(id, moduleId, displayKey, null, detailKey, parentId, changeImpact, visibleInGui, dependencies, defaultValue, ControlSpec.toggle());
    }

    public BooleanSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            boolean defaultValue,
            @Nullable ControlSpec controlSpec) {
        super(id, moduleId, displayKey, summaryKey, detailKey, parentId, changeImpact, visibleInGui, dependencies, defaultValue, controlSpec);
    }

    @Override
    public Class<Boolean> valueType() {
        return Boolean.class;
    }

    @Override
    public Boolean coerceValue(Object rawValue) {
        if (rawValue instanceof Boolean value) {
            return value;
        }
        if (rawValue instanceof String value) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException("Cannot coerce value to boolean: " + rawValue);
    }
}
