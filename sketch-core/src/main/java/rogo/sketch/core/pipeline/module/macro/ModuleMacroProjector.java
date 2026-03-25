package rogo.sketch.core.pipeline.module.macro;

import rogo.sketch.core.pipeline.module.setting.SettingSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
            registry.setFlag(ownerId, flagName, enabled);
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
            registry.setMacro(ownerId, macroName, mapper.apply(value));
        }
    }
}
