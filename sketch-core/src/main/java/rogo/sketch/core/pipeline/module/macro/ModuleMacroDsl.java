package rogo.sketch.core.pipeline.module.macro;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ModuleMacroDsl {
    private final String moduleId;
    private final Consumer<ModuleMacroDefinition> registrar;

    public ModuleMacroDsl(String moduleId, Consumer<ModuleMacroDefinition> registrar) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public MacroBuilder flag(String macroName) {
        return new MacroBuilder(macroName, ModuleMacroDefinition.MacroKind.FLAG).macroName(macroName);
    }

    public MacroBuilder value(String macroName) {
        return new MacroBuilder(macroName, ModuleMacroDefinition.MacroKind.VALUE).macroName(macroName);
    }

    public MacroBuilder constant(String macroName) {
        return new MacroBuilder(macroName, ModuleMacroDefinition.MacroKind.CONSTANT).macroName(macroName);
    }

    public ChoiceBuilder choice(String id) {
        return new ChoiceBuilder(id);
    }

    public class MacroBuilder {
        private final String id;
        private final ModuleMacroDefinition.MacroKind kind;
        private @Nullable String macroName;
        private @Nullable String constantValue;
        private @Nullable KeyId settingId;
        private @Nullable String displayKey;
        private @Nullable String summaryKey;
        private @Nullable String detailKey;
        private @Nullable ControlSpec controlSpec;

        private MacroBuilder(String id, ModuleMacroDefinition.MacroKind kind) {
            this.id = id;
            this.kind = kind;
        }

        public MacroBuilder macroName(@Nullable String macroName) {
            this.macroName = macroName;
            return this;
        }

        public MacroBuilder constantValue(@Nullable String constantValue) {
            this.constantValue = constantValue;
            return this;
        }

        public MacroBuilder setting(@Nullable KeyId settingId) {
            this.settingId = settingId;
            return this;
        }

        public MacroBuilder displayKey(@Nullable String displayKey) {
            this.displayKey = displayKey;
            return this;
        }

        public MacroBuilder summary(@Nullable String summaryKey) {
            this.summaryKey = summaryKey;
            return this;
        }

        public MacroBuilder detail(@Nullable String detailKey) {
            this.detailKey = detailKey;
            return this;
        }

        public MacroBuilder control(@Nullable ControlSpec controlSpec) {
            this.controlSpec = controlSpec;
            return this;
        }

        public ModuleMacroDefinition register() {
            ModuleMacroDefinition definition = new ModuleMacroDefinition(
                    moduleId,
                    id,
                    kind,
                    macroName,
                    constantValue,
                    settingId,
                    displayKey,
                    summaryKey,
                    detailKey,
                    controlSpec,
                    List.of());
            registrar.accept(definition);
            return definition;
        }
    }

    public final class ChoiceBuilder {
        private final String id;
        private @Nullable KeyId settingId;
        private @Nullable String displayKey;
        private @Nullable String summaryKey;
        private @Nullable String detailKey;
        private @Nullable ControlSpec controlSpec;
        private final List<MacroChoiceTarget> targets = new ArrayList<>();

        private ChoiceBuilder(String id) {
            this.id = id;
        }

        public ChoiceBuilder setting(@Nullable KeyId settingId) {
            this.settingId = settingId;
            return this;
        }

        public ChoiceBuilder displayKey(@Nullable String displayKey) {
            this.displayKey = displayKey;
            return this;
        }

        public ChoiceBuilder summary(@Nullable String summaryKey) {
            this.summaryKey = summaryKey;
            return this;
        }

        public ChoiceBuilder detail(@Nullable String detailKey) {
            this.detailKey = detailKey;
            return this;
        }

        public ChoiceBuilder control(@Nullable ControlSpec controlSpec) {
            this.controlSpec = controlSpec;
            return this;
        }

        public ChoiceBuilder option(String optionValue, String macroName) {
            targets.add(new MacroChoiceTarget(optionValue, macroName, null));
            return this;
        }

        public ChoiceBuilder optionValue(String optionValue, String macroName, String macroValue) {
            targets.add(new MacroChoiceTarget(optionValue, macroName, macroValue));
            return this;
        }

        public ModuleMacroDefinition register() {
            ModuleMacroDefinition definition = new ModuleMacroDefinition(
                    moduleId,
                    id,
                    ModuleMacroDefinition.MacroKind.CHOICE,
                    null,
                    null,
                    settingId,
                    displayKey,
                    summaryKey,
                    detailKey,
                    controlSpec,
                    targets);
            registrar.accept(definition);
            return definition;
        }
    }
}
