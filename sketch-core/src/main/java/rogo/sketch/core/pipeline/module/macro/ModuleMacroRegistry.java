package rogo.sketch.core.pipeline.module.macro;

import rogo.sketch.core.shader.config.MacroContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owner-scoped module macro projection registry.
 */
public class ModuleMacroRegistry {
    private final Map<String, Set<ModuleMacroDefinition>> definitionsByModule = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> appliedMacros = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> appliedFlags = new ConcurrentHashMap<>();

    public void registerDefinition(ModuleMacroDefinition definition) {
        definitionsByModule
                .computeIfAbsent(definition.moduleId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(definition);
    }

    public Set<ModuleMacroDefinition> definitionsForModule(String moduleId) {
        return Collections.unmodifiableSet(definitionsByModule.getOrDefault(moduleId, Collections.emptySet()));
    }

    public void setMacro(String ownerId, String macroName, String value) {
        appliedMacros.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>()).put(macroName, value);
        MacroContext.getInstance().setModuleMacro(ownerId, macroName, value);
    }

    public void removeMacro(String ownerId, String macroName) {
        Map<String, String> ownerMacros = appliedMacros.get(ownerId);
        if (ownerMacros != null) {
            ownerMacros.remove(macroName);
            if (ownerMacros.isEmpty()) {
                appliedMacros.remove(ownerId);
            }
        }
        MacroContext.getInstance().removeModuleMacro(ownerId, macroName);
    }

    public void setFlag(String ownerId, String flagName, boolean enabled) {
        Set<String> flags = appliedFlags.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet());
        if (enabled) {
            flags.add(flagName);
            MacroContext.getInstance().enableModuleFlag(ownerId, flagName);
        } else {
            flags.remove(flagName);
            MacroContext.getInstance().disableModuleFlag(ownerId, flagName);
        }
        if (flags.isEmpty()) {
            appliedFlags.remove(ownerId);
        }
    }

    public void clearOwner(String ownerId) {
        appliedMacros.remove(ownerId);
        appliedFlags.remove(ownerId);
        MacroContext.getInstance().clearModuleEntries(ownerId);
    }
}
