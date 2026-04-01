package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.control.NumericSpec;

import java.util.List;
import java.util.function.BooleanSupplier;

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
        this(id, moduleId, displayKey, detailKey, parentId, changeImpact, visibleInGui, () -> true, dependencies, defaultValue, minValue, maxValue, sliderSpec);
    }

    public FloatSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            BooleanSupplier visibilityRule,
            List<DependencyRule> dependencies,
            float defaultValue,
            float minValue,
            float maxValue,
            @Nullable SliderSpec sliderSpec) {
        this(id, moduleId, displayKey, null, detailKey, parentId, changeImpact, visibleInGui, visibilityRule, dependencies, defaultValue, minValue, maxValue, sliderSpec,
                sliderSpec != null
                        ? ControlSpec.slider(NumericSpec.floating(sliderSpec.min(), sliderSpec.max(), sliderSpec.step(), "%.2f"))
                        : ControlSpec.number(NumericSpec.floating(minValue, maxValue, 1.0D, "%.2f")));
    }

    public FloatSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            float defaultValue,
            float minValue,
            float maxValue,
            @Nullable SliderSpec sliderSpec,
            @Nullable ControlSpec controlSpec) {
        this(id, moduleId, displayKey, summaryKey, detailKey, parentId, changeImpact, visibleInGui, () -> true, dependencies, defaultValue, minValue, maxValue, sliderSpec, controlSpec);
    }

    public FloatSetting(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            BooleanSupplier visibilityRule,
            List<DependencyRule> dependencies,
            float defaultValue,
            float minValue,
            float maxValue,
            @Nullable SliderSpec sliderSpec,
            @Nullable ControlSpec controlSpec) {
        super(id, moduleId, displayKey, summaryKey, detailKey, parentId, changeImpact, visibleInGui, visibilityRule, dependencies, defaultValue, controlSpec);
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
