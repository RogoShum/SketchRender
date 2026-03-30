package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.ui.control.ChoiceOptionSpec;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlSpec;

import java.util.ArrayList;
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
        this(id, moduleId, displayKey, null, detailKey, parentId, changeImpact, visibleInGui, dependencies, enumType, defaultValue, allowedValues,
                defaultChoiceControl(allowedValues));
    }

    public EnumSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            Class<E> enumType,
            E defaultValue,
            List<E> allowedValues,
            @Nullable ControlSpec controlSpec) {
        super(id, moduleId, displayKey, summaryKey, detailKey, parentId, changeImpact, visibleInGui, dependencies, defaultValue, controlSpec);
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

    private static <E extends Enum<E>> ControlSpec defaultChoiceControl(List<E> allowedValues) {
        List<ChoiceOptionSpec> options = new ArrayList<>();
        for (E allowedValue : allowedValues) {
            String name = allowedValue.name();
            options.add(new ChoiceOptionSpec(name, name, null, null));
        }
        return ControlSpec.choice(new ChoiceSpec(options,
                allowedValues.size() <= 3 ? ChoicePresentation.SEGMENTED : ChoicePresentation.DROPDOWN));
    }
}
