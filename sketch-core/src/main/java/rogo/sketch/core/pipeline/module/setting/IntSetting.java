package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public class IntSetting extends SettingNode<Integer> {
    private final int minValue;
    private final int maxValue;
    private final @Nullable SliderSpec sliderSpec;

    public IntSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            int defaultValue,
            int minValue,
            int maxValue,
            @Nullable SliderSpec sliderSpec) {
        super(id, moduleId, displayKey, detailKey, parentId, changeImpact, visibleInGui, dependencies, defaultValue);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.sliderSpec = sliderSpec;
    }

    public int minValue() {
        return minValue;
    }

    public int maxValue() {
        return maxValue;
    }

    public @Nullable SliderSpec sliderSpec() {
        return sliderSpec;
    }

    @Override
    public Class<Integer> valueType() {
        return Integer.class;
    }

    @Override
    public Integer coerceValue(Object rawValue) {
        int value;
        if (rawValue instanceof Number number) {
            value = number.intValue();
        } else if (rawValue instanceof String stringValue) {
            value = Integer.parseInt(stringValue);
        } else {
            throw new IllegalArgumentException("Cannot coerce value to int: " + rawValue);
        }
        return Math.max(minValue, Math.min(maxValue, value));
    }
}
