package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.control.NumericSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ModuleSettingDsl {
    private final String moduleId;
    private final Consumer<SettingNode<?>> registrar;

    public ModuleSettingDsl(String moduleId, Consumer<SettingNode<?>> registrar) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public GroupBuilder group(KeyId id, String displayKey) {
        return new GroupBuilder(id, displayKey);
    }

    public BoolBuilder bool(KeyId id, String displayKey) {
        return new BoolBuilder(id, displayKey);
    }

    public IntBuilder integer(KeyId id, String displayKey) {
        return new IntBuilder(id, displayKey);
    }

    public FloatBuilder floating(KeyId id, String displayKey) {
        return new FloatBuilder(id, displayKey);
    }

    public <E extends Enum<E>> EnumBuilder<E> enumeration(KeyId id, String displayKey, Class<E> enumType) {
        return new EnumBuilder<>(id, displayKey, enumType);
    }

    public abstract class BaseBuilder<B extends BaseBuilder<B>> {
        protected final KeyId id;
        protected final String displayKey;
        protected @Nullable String summaryKey;
        protected @Nullable String detailKey;
        protected @Nullable KeyId parentId;
        protected ChangeImpact changeImpact = ChangeImpact.RUNTIME_ONLY;
        protected boolean visibleInGui = true;
        protected final List<DependencyRule> dependencies = new ArrayList<>();

        protected BaseBuilder(KeyId id, String displayKey) {
            this.id = id;
            this.displayKey = displayKey;
        }

        protected abstract B self();

        public B summary(@Nullable String summaryKey) {
            this.summaryKey = summaryKey;
            return self();
        }

        public B detail(@Nullable String detailKey) {
            this.detailKey = detailKey;
            return self();
        }

        public B parent(@Nullable KeyId parentId) {
            this.parentId = parentId;
            return self();
        }

        public B impact(ChangeImpact changeImpact) {
            this.changeImpact = changeImpact;
            return self();
        }

        public B hiddenInGui() {
            this.visibleInGui = false;
            return self();
        }

        public B dependency(DependencyRule dependencyRule) {
            this.dependencies.add(dependencyRule);
            return self();
        }
    }

    public final class GroupBuilder extends BaseBuilder<GroupBuilder> {
        private GroupBuilder(KeyId id, String displayKey) {
            super(id, displayKey);
        }

        @Override
        protected GroupBuilder self() {
            return this;
        }

        public SettingGroup register() {
            SettingGroup group = new SettingGroup(id, moduleId, displayKey, summaryKey, detailKey, parentId, visibleInGui, dependencies);
            registrar.accept(group);
            return group;
        }
    }

    public final class BoolBuilder extends BaseBuilder<BoolBuilder> {
        private boolean defaultValue;
        private @Nullable ControlSpec controlSpec = ControlSpec.toggle();

        private BoolBuilder(KeyId id, String displayKey) {
            super(id, displayKey);
        }

        @Override
        protected BoolBuilder self() {
            return this;
        }

        public BoolBuilder defaultValue(boolean defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public BoolBuilder control(ControlSpec controlSpec) {
            this.controlSpec = controlSpec;
            return this;
        }

        public BooleanSetting register() {
            BooleanSetting setting = new BooleanSetting(id, moduleId, displayKey, summaryKey, detailKey, parentId,
                    changeImpact, visibleInGui, dependencies, defaultValue, controlSpec);
            registrar.accept(setting);
            return setting;
        }
    }

    public final class IntBuilder extends BaseBuilder<IntBuilder> {
        private int defaultValue;
        private int minValue;
        private int maxValue;
        private @Nullable SliderSpec sliderSpec;
        private @Nullable ControlSpec controlSpec;

        private IntBuilder(KeyId id, String displayKey) {
            super(id, displayKey);
        }

        @Override
        protected IntBuilder self() {
            return this;
        }

        public IntBuilder range(int minValue, int maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        public IntBuilder defaultValue(int defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public IntBuilder slider(int minValue, int maxValue, int step, String formatPattern) {
            range(minValue, maxValue);
            this.sliderSpec = SliderSpec.of(minValue, maxValue, step);
            this.controlSpec = ControlSpec.slider(NumericSpec.integer(minValue, maxValue, step, formatPattern));
            return this;
        }

        public IntBuilder number(int minValue, int maxValue, int step, String formatPattern) {
            range(minValue, maxValue);
            this.sliderSpec = null;
            this.controlSpec = ControlSpec.number(NumericSpec.integer(minValue, maxValue, step, formatPattern));
            return this;
        }

        public IntSetting register() {
            IntSetting setting = new IntSetting(id, moduleId, displayKey, summaryKey, detailKey, parentId,
                    changeImpact, visibleInGui, dependencies, defaultValue, minValue, maxValue, sliderSpec, controlSpec);
            registrar.accept(setting);
            return setting;
        }
    }

    public final class FloatBuilder extends BaseBuilder<FloatBuilder> {
        private float defaultValue;
        private float minValue;
        private float maxValue;
        private @Nullable SliderSpec sliderSpec;
        private @Nullable ControlSpec controlSpec;

        private FloatBuilder(KeyId id, String displayKey) {
            super(id, displayKey);
        }

        @Override
        protected FloatBuilder self() {
            return this;
        }

        public FloatBuilder range(float minValue, float maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        public FloatBuilder defaultValue(float defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public FloatBuilder slider(float minValue, float maxValue, float step, String formatPattern) {
            range(minValue, maxValue);
            this.sliderSpec = SliderSpec.of(minValue, maxValue, step);
            this.controlSpec = ControlSpec.slider(NumericSpec.floating(minValue, maxValue, step, formatPattern));
            return this;
        }

        public FloatBuilder number(float minValue, float maxValue, float step, String formatPattern) {
            range(minValue, maxValue);
            this.sliderSpec = null;
            this.controlSpec = ControlSpec.number(NumericSpec.floating(minValue, maxValue, step, formatPattern));
            return this;
        }

        public FloatSetting register() {
            FloatSetting setting = new FloatSetting(id, moduleId, displayKey, summaryKey, detailKey, parentId,
                    changeImpact, visibleInGui, dependencies, defaultValue, minValue, maxValue, sliderSpec, controlSpec);
            registrar.accept(setting);
            return setting;
        }
    }

    public final class EnumBuilder<E extends Enum<E>> extends BaseBuilder<EnumBuilder<E>> {
        private final Class<E> enumType;
        private final List<E> allowedValues = new ArrayList<>();
        private E defaultValue;
        private @Nullable ControlSpec controlSpec;

        private EnumBuilder(KeyId id, String displayKey, Class<E> enumType) {
            super(id, displayKey);
            this.enumType = enumType;
        }

        @Override
        protected EnumBuilder<E> self() {
            return this;
        }

        public EnumBuilder<E> values(List<E> allowedValues) {
            this.allowedValues.clear();
            this.allowedValues.addAll(allowedValues);
            return this;
        }

        public EnumBuilder<E> defaultValue(E defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public EnumBuilder<E> choice(ChoiceSpec choiceSpec) {
            this.controlSpec = ControlSpec.choice(choiceSpec);
            return this;
        }

        public EnumSetting<E> register() {
            List<E> values = allowedValues.isEmpty() ? List.of(enumType.getEnumConstants()) : List.copyOf(allowedValues);
            E resolvedDefault = defaultValue != null ? defaultValue : values.get(0);
            EnumSetting<E> setting = new EnumSetting<>(id, moduleId, displayKey, summaryKey, detailKey, parentId,
                    changeImpact, visibleInGui, dependencies, enumType, resolvedDefault, values, controlSpec);
            registrar.accept(setting);
            return setting;
        }
    }
}
