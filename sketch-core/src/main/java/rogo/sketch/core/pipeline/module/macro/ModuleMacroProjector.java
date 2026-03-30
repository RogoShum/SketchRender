package rogo.sketch.core.pipeline.module.macro;

import rogo.sketch.core.pipeline.module.setting.SettingSnapshot;
import rogo.sketch.core.shader.config.MacroEntryDescriptor;
import rogo.sketch.core.shader.config.MacroEntryType;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.function.Function;

/**
 * Projects typed setting values into module-owned macro flags/values.
 */
public class ModuleMacroProjector {
    private final List<ProjectionRule> rules = new ArrayList<>();

    public ModuleMacroProjector projectFlag(KeyId settingId, String flagName) {
        rules.add(new FlagRule(settingId, flagName));
        return this;
    }

    public ModuleMacroProjector projectValue(KeyId settingId, String macroName, Function<Object, String> mapper) {
        rules.add(new ValueRule(settingId, macroName, mapper));
        return this;
    }

    public ModuleMacroProjector projectChoice(
            KeyId settingId,
            Function<Object, String> selector,
            List<MacroChoiceTarget> targets) {
        rules.add(new ChoiceRule(settingId, selector, List.copyOf(targets)));
        return this;
    }

    public void apply(String ownerId, SettingSnapshot snapshot, ModuleMacroRegistry registry) {
        Objects.requireNonNull(snapshot, "snapshot");
        for (ProjectionRule rule : rules) {
            rule.apply(ownerId, snapshot, registry);
        }
    }

    private interface ProjectionRule {
        void apply(String ownerId, SettingSnapshot snapshot, ModuleMacroRegistry registry);
    }

    private record FlagRule(KeyId settingId, String flagName) implements ProjectionRule {
        @Override
        public void apply(String ownerId, SettingSnapshot snapshot, ModuleMacroRegistry registry) {
            boolean enabled = snapshot.isActive(settingId) && Boolean.TRUE.equals(snapshot.value(settingId));
            if (enabled) {
                registry.setFlag(ownerId, flagName, true, new MacroEntryDescriptor(flagName, MacroEntryType.FLAG, true, "1", null, null, null, null));
            } else {
                registry.setFlag(ownerId, flagName, false, null);
            }
        }
    }

    private record ValueRule(
            KeyId settingId,
            String macroName,
            Function<Object, String> mapper
    ) implements ProjectionRule {
        @Override
        public void apply(String ownerId, SettingSnapshot snapshot, ModuleMacroRegistry registry) {
            if (!snapshot.isActive(settingId)) {
                registry.removeMacro(ownerId, macroName);
                return;
            }
            Object value = snapshot.value(settingId);
            if (value == null) {
                registry.removeMacro(ownerId, macroName);
                return;
            }
            String mappedValue = mapper.apply(value);
            registry.setMacro(ownerId, macroName, mappedValue,
                    new MacroEntryDescriptor(macroName, MacroEntryType.VALUE, true, mappedValue, null, null, null, null));
        }
    }

    private record ChoiceRule(
            KeyId settingId,
            Function<Object, String> selector,
            List<MacroChoiceTarget> targets
    ) implements ProjectionRule {
        @Override
        public void apply(String ownerId, SettingSnapshot snapshot, ModuleMacroRegistry registry) {
            Set<String> names = new LinkedHashSet<>();
            for (MacroChoiceTarget target : targets) {
                names.add(target.macroName());
            }
            if (!snapshot.isActive(settingId)) {
                for (String name : names) {
                    registry.removeMacro(ownerId, name);
                    registry.setFlag(ownerId, name, false, null);
                }
                return;
            }
            Object rawValue = snapshot.value(settingId);
            String selected = rawValue != null ? selector.apply(rawValue) : null;
            for (MacroChoiceTarget target : targets) {
                boolean active = Objects.equals(target.optionValue(), selected);
                if (target.flag()) {
                    registry.setFlag(ownerId, target.macroName(), active,
                            active ? new MacroEntryDescriptor(target.macroName(), MacroEntryType.CHOICE, true, "1", null, null, null, null) : null);
                } else if (active) {
                    registry.setMacro(ownerId, target.macroName(), target.macroValue(),
                            new MacroEntryDescriptor(target.macroName(), MacroEntryType.CHOICE, true, target.macroValue(), null, null, null, null));
                } else {
                    registry.removeMacro(ownerId, target.macroName());
                }
            }
        }
    }
}
