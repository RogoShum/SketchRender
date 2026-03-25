package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public class FloatSetting extends SettingNode<Float> {
    private final float minValue;
    private final float maxValue;
    private final @Nullable SliderSpec sliderSpec;

    public FloatSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            float defaultValue,
            float minValue,
            float maxValue,
            @Nullable SliderSpec sliderSpec) {
        super(id, moduleId, displayKey, detailKey, parentId, changeImpact, visibleInGui, dependencies, defaultValue);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.sliderSpec = sliderSpec;
    }

    public float minValue() {
        return minValue;
    }

    public float maxValue() {
        return maxValue;
    }

    public @Nullable SliderSpec sliderSpec() {
        return sliderSpec;
    }

    @Override
    public Class<Float> valueType() {
        return Float.class;
    }

    @Override
    public Float coerceValue(Object rawValue) {
        float value;
        if (rawValue instanceof Number number) {
            value = number.floatValue();
        } else if (rawValue instanceof String stringValue) {
            value = Float.parseFloat(stringValue);
        } else {
            throw new IllegalArgumentException("Cannot coerce value to float: " + rawValue);
        }
        return Math.max(minValue, Math.min(maxValue, value));
    }
}
