package rogo.sketch.core.pipeline.module.macro;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleMacroDefinition {
    private final String moduleId;
    private final String id;
    private final MacroKind kind;
    private final @Nullable String macroName;
    private final @Nullable String constantValue;
    private final @Nullable KeyId settingId;
    private final @Nullable String displayKey;
    private final @Nullable String summaryKey;
    private final @Nullable String detailKey;
    private final @Nullable ControlSpec controlSpec;
    private final List<MacroChoiceTarget> choiceTargets;

    public ModuleMacroDefinition(
            String moduleId,
            String id,
            MacroKind kind,
            @Nullable String macroName,
            @Nullable String constantValue,
            @Nullable KeyId settingId,
            @Nullable String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            @Nullable ControlSpec controlSpec,
            List<MacroChoiceTarget> choiceTargets) {
        this.moduleId = moduleId;
        this.id = id;
        this.kind = kind;
        this.macroName = macroName;
        this.constantValue = constantValue;
        this.settingId = settingId;
        this.displayKey = displayKey;
        this.summaryKey = summaryKey;
        this.detailKey = detailKey;
        this.controlSpec = controlSpec;
        this.choiceTargets = Collections.unmodifiableList(new ArrayList<>(choiceTargets));
    }

    public String moduleId() {
        return moduleId;
    }

    public String id() {
        return id;
    }

    public MacroKind kind() {
        return kind;
    }

    public @Nullable String macroName() {
        return macroName;
    }

    public @Nullable String constantValue() {
        return constantValue;
    }

    public @Nullable KeyId settingId() {
        return settingId;
    }

    public @Nullable String displayKey() {
        return displayKey;
    }

    public @Nullable String summaryKey() {
        return summaryKey;
    }

    public @Nullable String detailKey() {
        return detailKey;
    }

    public @Nullable ControlSpec controlSpec() {
        return controlSpec;
    }

    public List<MacroChoiceTarget> choiceTargets() {
        return choiceTargets;
    }

    public List<String> declaredMacroNames() {
        List<String> names = new ArrayList<>();
        if (macroName != null) {
            names.add(macroName);
        }
        for (MacroChoiceTarget target : choiceTargets) {
            names.add(target.macroName());
        }
        return Collections.unmodifiableList(names);
    }

    public enum MacroKind {
        FLAG,
        VALUE,
        CHOICE,
        CONSTANT
    }
}
