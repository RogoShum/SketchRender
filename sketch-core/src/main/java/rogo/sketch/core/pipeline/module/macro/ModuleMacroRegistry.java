package rogo.sketch.core.pipeline.module.macro;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.shader.config.MacroEntryDescriptor;
import rogo.sketch.core.shader.config.MacroEntryType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owner-scoped module and compat macro projection registry.
 */
public class ModuleMacroRegistry {
    private final Map<String, List<ModuleMacroDefinition>> definitionsByModule = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> appliedMacros = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> appliedFlags = new ConcurrentHashMap<>();
    private final Map<String, Map<String, MacroEntryDescriptor>> appliedEntries = new ConcurrentHashMap<>();

    public void registerDefinition(ModuleMacroDefinition definition) {
        definitionsByModule.computeIfAbsent(definition.moduleId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(definition);
    }

    public List<ModuleMacroDefinition> definitionsForModule(String moduleId) {
        return List.copyOf(definitionsByModule.getOrDefault(moduleId, List.of()));
    }

    public List<ModuleMacroDefinition> allDefinitions() {
        List<ModuleMacroDefinition> definitions = new ArrayList<>();
        for (List<ModuleMacroDefinition> value : definitionsByModule.values()) {
            definitions.addAll(value);
        }
        return List.copyOf(definitions);
    }

    public void setMacro(String ownerId, String macroName, String value) {
        setMacro(ownerId, macroName, value, new MacroEntryDescriptor(macroName, MacroEntryType.VALUE, true, value, null, null, null, null));
    }

    public void setMacro(String ownerId, String macroName, String value, @Nullable MacroEntryDescriptor descriptor) {
        appliedMacros.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>()).put(macroName, value);
        if (descriptor != null) {
            appliedEntries.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>()).put(macroName, descriptor);
        }
        MacroContext.getInstance().setModuleMacro(ownerId, macroName, value,
                descriptor != null ? descriptor : MacroEntryDescriptor.constantValue(macroName, value));
    }

    public void removeMacro(String ownerId, String macroName) {
        Map<String, String> ownerMacros = appliedMacros.get(ownerId);
        if (ownerMacros != null) {
            ownerMacros.remove(macroName);
            if (ownerMacros.isEmpty()) {
                appliedMacros.remove(ownerId);
            }
        }
        Map<String, MacroEntryDescriptor> ownerEntries = appliedEntries.get(ownerId);
        if (ownerEntries != null) {
            ownerEntries.remove(macroName);
            if (ownerEntries.isEmpty()) {
                appliedEntries.remove(ownerId);
            }
        }
        MacroContext.getInstance().removeModuleMacro(ownerId, macroName);
    }

    public void setFlag(String ownerId, String flagName, boolean enabled) {
        setFlag(ownerId, flagName, enabled, enabled ? new MacroEntryDescriptor(flagName, MacroEntryType.FLAG, true, "1", null, null, null, null) : null);
    }

    public void setFlag(String ownerId, String flagName, boolean enabled, @Nullable MacroEntryDescriptor descriptor) {
        Set<String> flags = appliedFlags.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet());
        if (enabled) {
            flags.add(flagName);
            if (descriptor != null) {
                appliedEntries.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>()).put(flagName, descriptor);
            }
            MacroContext.getInstance().enableModuleFlag(ownerId, flagName,
                    descriptor != null ? descriptor : MacroEntryDescriptor.constantFlag(flagName));
        } else {
            flags.remove(flagName);
            Map<String, MacroEntryDescriptor> ownerEntries = appliedEntries.get(ownerId);
            if (ownerEntries != null) {
                ownerEntries.remove(flagName);
                if (ownerEntries.isEmpty()) {
                    appliedEntries.remove(ownerId);
                }
            }
            MacroContext.getInstance().disableModuleFlag(ownerId, flagName);
        }
        if (flags.isEmpty()) {
            appliedFlags.remove(ownerId);
        }
    }

    public void setGlobalFlag(String ownerId, String flagName, boolean enabled) {
        if (enabled) {
            MacroContext.getInstance().enableOwnedGlobalFlag(ownerId, flagName,
                    new MacroEntryDescriptor(flagName, MacroEntryType.CONSTANT, false, "1", null, null, null, null));
        } else {
            MacroContext.getInstance().clearOwnedGlobalEntries(ownerId);
        }
    }

    public void setGlobalMacro(String ownerId, String macroName, String value) {
        MacroContext.getInstance().setOwnedGlobalMacro(ownerId, macroName, value,
                new MacroEntryDescriptor(macroName, MacroEntryType.CONSTANT, false, value, null, null, null, null));
    }

    public void clearOwner(String ownerId) {
        appliedMacros.remove(ownerId);
        appliedFlags.remove(ownerId);
        appliedEntries.remove(ownerId);
        MacroContext.getInstance().clearModuleEntries(ownerId);
        MacroContext.getInstance().clearOwnedGlobalEntries(ownerId);
    }

    public Map<String, Map<String, String>> appliedMacrosSnapshot() {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : appliedMacros.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Set<String>> appliedFlagsSnapshot() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : appliedFlags.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Map<String, MacroEntryDescriptor>> appliedEntriesSnapshot() {
        Map<String, Map<String, MacroEntryDescriptor>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, MacroEntryDescriptor>> entry : appliedEntries.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
