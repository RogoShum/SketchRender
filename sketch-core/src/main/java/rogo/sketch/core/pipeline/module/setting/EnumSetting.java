package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class EnumSetting<E extends Enum<E>> extends SettingNode<E> {
    private final Class<E> enumType;
    private final List<E> allowedValues;

    public EnumSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            Class<E> enumType,
            E defaultValue,
            List<E> allowedValues) {
        super(id, moduleId, displayKey, detailKey, parentId, changeImpact, visibleInGui, dependencies, defaultValue);
        this.enumType = Objects.requireNonNull(enumType, "enumType");
        this.allowedValues = Collections.unmodifiableList(allowedValues);
    }

    public List<E> allowedValues() {
        return allowedValues;
    }

    @Override
    public Class<E> valueType() {
        return enumType;
    }

    @Override
    public E coerceValue(Object rawValue) {
        if (enumType.isInstance(rawValue)) {
            return enumType.cast(rawValue);
        }
        if (rawValue instanceof String stringValue) {
            return Enum.valueOf(enumType, stringValue);
        }
        throw new IllegalArgumentException("Cannot coerce value to enum " + enumType.getName() + ": " + rawValue);
    }
}
