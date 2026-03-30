package rogo.sketch.core.shader.config;

import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Hierarchical macro context for shader compilation and dashboard inspection.
 */
public class MacroContext {
    private static MacroContext instance;

    private final Map<String, String> globalMacros = new ConcurrentHashMap<>();
    private final Set<String> globalFlags = ConcurrentHashMap.newKeySet();
    private final Map<String, MacroEntryDescriptor> globalEntries = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> ownedGlobalMacros = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> ownedGlobalFlags = new ConcurrentHashMap<>();
    private final Map<String, Map<String, MacroEntryDescriptor>> ownedGlobalEntries = new ConcurrentHashMap<>();

    private final Map<String, Map<String, String>> resourcePackMacros = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> resourcePackFlags = new ConcurrentHashMap<>();
    private final Map<String, Map<String, MacroEntryDescriptor>> resourcePackEntries = new ConcurrentHashMap<>();

    private final Map<String, String> configMacros = new ConcurrentHashMap<>();
    private final Set<String> configFlags = ConcurrentHashMap.newKeySet();
    private final Map<String, MacroEntryDescriptor> configEntries = new ConcurrentHashMap<>();

    private final Map<String, Map<String, String>> moduleMacros = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> moduleFlags = new ConcurrentHashMap<>();
    private final Map<String, Map<String, MacroEntryDescriptor>> moduleEntries = new ConcurrentHashMap<>();

    private final List<Consumer<MacroChangeEvent>> changeListeners = new ArrayList<>();
    private final Map<String, Set<KeyId>> macroToShaders = new ConcurrentHashMap<>();
    private final Map<KeyId, MacroTemplate> registeredTemplates = new ConcurrentHashMap<>();

    private MacroContext() {
    }

    public static synchronized MacroContext getInstance() {
        if (instance == null) {
            instance = new MacroContext();
        }
        return instance;
    }

    public void setGlobalMacro(String name, String value) {
        setGlobalMacro(name, value, MacroEntryDescriptor.constantValue(name, value));
    }

    public void setGlobalMacro(String name, String value, MacroEntryDescriptor descriptor) {
        String oldValue = globalMacros.put(name, value);
        globalEntries.put(name, descriptor);
        if (!Objects.equals(oldValue, value)) {
            notifyChange(MacroLayer.GLOBAL, name, oldValue, value);
        }
    }

    public void enableGlobalFlag(String flag) {
        enableGlobalFlag(flag, MacroEntryDescriptor.constantFlag(flag));
    }

    public void enableGlobalFlag(String flag, MacroEntryDescriptor descriptor) {
        globalEntries.put(flag, descriptor);
        if (globalFlags.add(flag)) {
            notifyChange(MacroLayer.GLOBAL, flag, null, "1");
        }
    }

    public void disableGlobalFlag(String flag) {
        if (globalFlags.remove(flag)) {
            notifyChange(MacroLayer.GLOBAL, flag, "1", null);
        }
        globalEntries.remove(flag);
    }

    public void removeGlobalMacro(String name) {
        String oldValue = globalMacros.remove(name);
        globalEntries.remove(name);
        if (oldValue != null) {
            notifyChange(MacroLayer.GLOBAL, name, oldValue, null);
        }
    }

    public void setOwnedGlobalMacro(String ownerId, String name, String value, MacroEntryDescriptor descriptor) {
        Map<String, String> macros = ownedGlobalMacros.computeIfAbsent(ownerId, ignored -> new ConcurrentHashMap<>());
        Map<String, MacroEntryDescriptor> entries = ownedGlobalEntries.computeIfAbsent(ownerId, ignored -> new ConcurrentHashMap<>());
        String oldValue = macros.put(name, value);
        entries.put(name, descriptor);
        if (!Objects.equals(oldValue, value)) {
            notifyChange(MacroLayer.GLOBAL, name, oldValue, value);
        }
    }

    public void enableOwnedGlobalFlag(String ownerId, String flag, MacroEntryDescriptor descriptor) {
        Set<String> flags = ownedGlobalFlags.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet());
        Map<String, MacroEntryDescriptor> entries = ownedGlobalEntries.computeIfAbsent(ownerId, ignored -> new ConcurrentHashMap<>());
        entries.put(flag, descriptor);
        if (flags.add(flag)) {
            notifyChange(MacroLayer.GLOBAL, flag, null, "1");
        }
    }

    public void clearOwnedGlobalEntries(String ownerId) {
        Map<String, String> removedMacros = ownedGlobalMacros.remove(ownerId);
        if (removedMacros != null) {
            for (Map.Entry<String, String> entry : removedMacros.entrySet()) {
                notifyChange(MacroLayer.GLOBAL, entry.getKey(), entry.getValue(), null);
            }
        }
        Set<String> removedFlags = ownedGlobalFlags.remove(ownerId);
        if (removedFlags != null) {
            for (String flag : removedFlags) {
                notifyChange(MacroLayer.GLOBAL, flag, "1", null);
            }
        }
        ownedGlobalEntries.remove(ownerId);
    }

    public void registerResourcePackMacros(String packId, Map<String, String> macros) {
        Map<String, MacroEntryDescriptor> descriptors = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : macros.entrySet()) {
            descriptors.put(entry.getKey(), MacroEntryDescriptor.constantValue(entry.getKey(), entry.getValue()));
        }
        registerResourcePackEntries(packId, descriptors);
    }

    public void registerResourcePackFlags(String packId, Set<String> flags) {
        Map<String, MacroEntryDescriptor> descriptors = new LinkedHashMap<>();
        for (String flag : flags) {
            descriptors.put(flag, MacroEntryDescriptor.constantFlag(flag));
        }
        registerResourcePackEntries(packId, descriptors);
    }

    public void registerResourcePackEntries(String packId, Map<String, MacroEntryDescriptor> entries) {
        Map<String, String> macros = new LinkedHashMap<>();
        Set<String> flags = new LinkedHashSet<>();
        for (MacroEntryDescriptor descriptor : entries.values()) {
            if (descriptor.type() == MacroEntryType.FLAG) {
                flags.add(descriptor.name());
            } else {
                macros.put(descriptor.name(), Objects.toString(descriptor.value(), ""));
            }
        }
        resourcePackMacros.put(packId, macros);
        resourcePackFlags.put(packId, flags);
        resourcePackEntries.put(packId, new LinkedHashMap<>(entries));
        for (MacroEntryDescriptor descriptor : entries.values()) {
            String value = descriptor.type() == MacroEntryType.FLAG ? "1" : Objects.toString(descriptor.value(), "");
            notifyChange(MacroLayer.RESOURCE_PACK, descriptor.name(), null, value);
        }
    }

    public void unregisterResourcePack(String packId) {
        Map<String, String> removedMacros = resourcePackMacros.remove(packId);
        if (removedMacros != null) {
            for (Map.Entry<String, String> entry : removedMacros.entrySet()) {
                notifyChange(MacroLayer.RESOURCE_PACK, entry.getKey(), entry.getValue(), null);
            }
        }

        Set<String> removedFlags = resourcePackFlags.remove(packId);
        if (removedFlags != null) {
            for (String flag : removedFlags) {
                notifyChange(MacroLayer.RESOURCE_PACK, flag, "1", null);
            }
        }
        resourcePackEntries.remove(packId);
    }

    public void setConfigMacro(String name, String value) {
        String oldValue = configMacros.put(name, value);
        configEntries.put(name, MacroEntryDescriptor.constantValue(name, value));
        if (!Objects.equals(oldValue, value)) {
            notifyChange(MacroLayer.CONFIG, name, oldValue, value);
        }
    }

    public void enableConfigFlag(String flag) {
        configEntries.put(flag, MacroEntryDescriptor.constantFlag(flag));
        if (configFlags.add(flag)) {
            notifyChange(MacroLayer.CONFIG, flag, null, "1");
        }
    }

    public void disableConfigFlag(String flag) {
        if (configFlags.remove(flag)) {
            notifyChange(MacroLayer.CONFIG, flag, "1", null);
        }
        configEntries.remove(flag);
    }

    public void removeConfigMacro(String name) {
        String oldValue = configMacros.remove(name);
        configEntries.remove(name);
        if (oldValue != null) {
            notifyChange(MacroLayer.CONFIG, name, oldValue, null);
        }
    }

    public void setModuleMacro(String moduleId, String name, String value) {
        setModuleMacro(moduleId, name, value, MacroEntryDescriptor.constantValue(name, value));
    }

    public void setModuleMacro(String moduleId, String name, String value, MacroEntryDescriptor descriptor) {
        Map<String, String> macros = moduleMacros.computeIfAbsent(moduleId, ignored -> new ConcurrentHashMap<>());
        Map<String, MacroEntryDescriptor> entries = moduleEntries.computeIfAbsent(moduleId, ignored -> new ConcurrentHashMap<>());
        String oldValue = macros.put(name, value);
        entries.put(name, descriptor);
        if (!Objects.equals(oldValue, value)) {
            notifyChange(MacroLayer.MODULE, name, oldValue, value);
        }
    }

    public void removeModuleMacro(String moduleId, String name) {
        Map<String, String> macros = moduleMacros.get(moduleId);
        if (macros == null) {
            return;
        }
        String oldValue = macros.remove(name);
        Map<String, MacroEntryDescriptor> entries = moduleEntries.get(moduleId);
        if (entries != null) {
            entries.remove(name);
            if (entries.isEmpty()) {
                moduleEntries.remove(moduleId);
            }
        }
        if (oldValue != null) {
            notifyChange(MacroLayer.MODULE, name, oldValue, null);
        }
        if (macros.isEmpty()) {
            moduleMacros.remove(moduleId);
        }
    }

    public void enableModuleFlag(String moduleId, String flag) {
        enableModuleFlag(moduleId, flag, MacroEntryDescriptor.constantFlag(flag));
    }

    public void enableModuleFlag(String moduleId, String flag, MacroEntryDescriptor descriptor) {
        Set<String> flags = moduleFlags.computeIfAbsent(moduleId, ignored -> ConcurrentHashMap.newKeySet());
        Map<String, MacroEntryDescriptor> entries = moduleEntries.computeIfAbsent(moduleId, ignored -> new ConcurrentHashMap<>());
        entries.put(flag, descriptor);
        if (flags.add(flag)) {
            notifyChange(MacroLayer.MODULE, flag, null, "1");
        }
    }

    public void disableModuleFlag(String moduleId, String flag) {
        Set<String> flags = moduleFlags.get(moduleId);
        if (flags == null) {
            return;
        }
        if (flags.remove(flag)) {
            notifyChange(MacroLayer.MODULE, flag, "1", null);
        }
        Map<String, MacroEntryDescriptor> entries = moduleEntries.get(moduleId);
        if (entries != null) {
            entries.remove(flag);
            if (entries.isEmpty()) {
                moduleEntries.remove(moduleId);
            }
        }
        if (flags.isEmpty()) {
            moduleFlags.remove(moduleId);
        }
    }

    public void clearModuleEntries(String moduleId) {
        Map<String, String> removedMacros = moduleMacros.remove(moduleId);
        if (removedMacros != null) {
            for (Map.Entry<String, String> entry : removedMacros.entrySet()) {
                notifyChange(MacroLayer.MODULE, entry.getKey(), entry.getValue(), null);
            }
        }
        Set<String> removedFlags = moduleFlags.remove(moduleId);
        if (removedFlags != null) {
            for (String flag : removedFlags) {
                notifyChange(MacroLayer.MODULE, flag, "1", null);
            }
        }
        moduleEntries.remove(moduleId);
    }

    public Map<String, String> getMergedMacros() {
        return getMergedMacros(Collections.emptySet());
    }

    public Map<String, String> getMergedMacros(Set<String> dynamicFlags) {
        Map<String, String> merged = new LinkedHashMap<>();

        merged.putAll(configMacros);
        for (Map<String, String> macros : moduleMacros.values()) {
            merged.putAll(macros);
        }
        for (Map<String, String> macros : resourcePackMacros.values()) {
            merged.putAll(macros);
        }
        for (Map<String, String> macros : ownedGlobalMacros.values()) {
            merged.putAll(macros);
        }
        merged.putAll(globalMacros);

        for (String flag : configFlags) {
            merged.put(flag, "1");
        }
        for (Set<String> flags : moduleFlags.values()) {
            for (String flag : flags) {
                merged.put(flag, "1");
            }
        }
        for (Set<String> flags : resourcePackFlags.values()) {
            for (String flag : flags) {
                merged.put(flag, "1");
            }
        }
        for (Set<String> flags : ownedGlobalFlags.values()) {
            for (String flag : flags) {
                merged.put(flag, "1");
            }
        }
        for (String flag : globalFlags) {
            merged.put(flag, "1");
        }
        for (String flag : dynamicFlags) {
            merged.put(flag, "1");
        }

        return merged;
    }

    public List<MacroSnapshotEntry> mergedEntrySnapshot() {
        return mergedEntrySnapshot(Collections.emptySet());
    }

    public List<MacroSnapshotEntry> mergedEntrySnapshot(Set<String> dynamicFlags) {
        LinkedHashMap<String, MacroSnapshotEntry> resolved = new LinkedHashMap<>();
        mergeEntries(resolved, MacroLayer.CONFIG, null, null, configEntries);
        for (Map.Entry<String, Map<String, MacroEntryDescriptor>> entry : moduleEntries.entrySet()) {
            mergeEntries(resolved, MacroLayer.MODULE, entry.getKey(), entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Map<String, MacroEntryDescriptor>> entry : resourcePackEntries.entrySet()) {
            mergeEntries(resolved, MacroLayer.RESOURCE_PACK, entry.getKey(), null, entry.getValue());
        }
        for (Map.Entry<String, Map<String, MacroEntryDescriptor>> entry : ownedGlobalEntries.entrySet()) {
            mergeEntries(resolved, MacroLayer.GLOBAL, entry.getKey(), entry.getKey(), entry.getValue());
        }
        mergeEntries(resolved, MacroLayer.GLOBAL, null, null, globalEntries);
        for (String dynamicFlag : dynamicFlags) {
            resolved.put(dynamicFlag, new MacroSnapshotEntry(dynamicFlag, "1", MacroLayer.DYNAMIC, null, null,
                    MacroEntryType.FLAG, false, null, null, null, null));
        }
        List<MacroSnapshotEntry> entries = new ArrayList<>(resolved.values());
        entries.sort(Comparator.comparing(MacroSnapshotEntry::name));
        return Collections.unmodifiableList(entries);
    }

    public List<MacroSnapshotEntry> templateEntrySnapshot() {
        List<MacroSnapshotEntry> entries = new ArrayList<>();
        for (Map.Entry<KeyId, MacroTemplate> entry : registeredTemplates.entrySet()) {
            for (MacroSnapshotEntry snapshotEntry : entry.getValue().snapshotEntries()) {
                entries.add(new MacroSnapshotEntry(
                        snapshotEntry.name(),
                        snapshotEntry.value(),
                        MacroLayer.TEMPLATE,
                        entry.getKey().toString(),
                        null,
                        snapshotEntry.type(),
                        snapshotEntry.editable(),
                        snapshotEntry.displayKey(),
                        snapshotEntry.summaryKey(),
                        snapshotEntry.detailKey(),
                        snapshotEntry.controlSpec()));
            }
        }
        entries.sort(Comparator.comparing(MacroSnapshotEntry::sourceId, Comparator.nullsLast(String::compareTo))
                .thenComparing(MacroSnapshotEntry::name));
        return Collections.unmodifiableList(entries);
    }

    private void mergeEntries(
            Map<String, MacroSnapshotEntry> resolved,
            MacroLayer layer,
            String sourceId,
            String ownerId,
            Map<String, MacroEntryDescriptor> entries) {
        for (MacroEntryDescriptor descriptor : entries.values()) {
            String value = descriptor.type() == MacroEntryType.FLAG ? "1" : Objects.toString(descriptor.value(), "");
            resolved.put(descriptor.name(), new MacroSnapshotEntry(
                    descriptor.name(),
                    value,
                    layer,
                    sourceId,
                    ownerId,
                    descriptor.type(),
                    descriptor.editable(),
                    descriptor.displayKey(),
                    descriptor.summaryKey(),
                    descriptor.detailKey(),
                    descriptor.controlSpec()));
        }
    }

    public boolean isDefined(String name) {
        return getMergedMacros().containsKey(name);
    }

    public void registerMacroTemplate(KeyId id, MacroTemplate template) {
        registeredTemplates.put(id, template);
    }

    public void unregisterMacroTemplate(KeyId id) {
        registeredTemplates.remove(id);
    }

    public MacroTemplate getMacroTemplate(KeyId id) {
        return registeredTemplates.get(id);
    }

    public Map<String, String> getMergedMacros(Set<String> dynamicFlags, Set<KeyId> templateIds) {
        Map<String, String> merged = getMergedMacros(dynamicFlags);
        for (KeyId templateId : templateIds) {
            MacroTemplate template = registeredTemplates.get(templateId);
            if (template != null && template.isConditionMet(this)) {
                merged = template.mergeWith(merged);
            }
        }
        return merged;
    }

    public void registerShaderDependencies(KeyId shaderId, Set<String> usedMacros) {
        for (String macro : usedMacros) {
            macroToShaders.computeIfAbsent(macro, k -> ConcurrentHashMap.newKeySet()).add(shaderId);
        }
    }

    public void unregisterShaderDependencies(KeyId shaderId) {
        for (Set<KeyId> shaders : macroToShaders.values()) {
            shaders.remove(shaderId);
        }
    }

    public Set<KeyId> getAffectedShaders(String macroName) {
        Set<KeyId> affected = macroToShaders.get(macroName);
        return affected != null ? new HashSet<>(affected) : Collections.emptySet();
    }

    public void addChangeListener(Consumer<MacroChangeEvent> listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Consumer<MacroChangeEvent> listener) {
        changeListeners.remove(listener);
    }

    private void notifyChange(MacroLayer layer, String name, String oldValue, String newValue) {
        MacroChangeEvent event = new MacroChangeEvent(layer, name, oldValue, newValue, getAffectedShaders(name));
        for (Consumer<MacroChangeEvent> listener : changeListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                SketchDiagnostics.get().warn("macro-context", "Error in macro change listener", e);
            }
        }
    }

    public void clear() {
        globalMacros.clear();
        globalFlags.clear();
        globalEntries.clear();
        ownedGlobalMacros.clear();
        ownedGlobalFlags.clear();
        ownedGlobalEntries.clear();
        resourcePackMacros.clear();
        resourcePackFlags.clear();
        resourcePackEntries.clear();
        configMacros.clear();
        configFlags.clear();
        configEntries.clear();
        moduleMacros.clear();
        moduleFlags.clear();
        moduleEntries.clear();
        macroToShaders.clear();
    }

    public void clearResourcePackMacros() {
        Set<String> affectedMacros = new LinkedHashSet<>();
        for (Map<String, MacroEntryDescriptor> entries : resourcePackEntries.values()) {
            affectedMacros.addAll(entries.keySet());
        }
        resourcePackMacros.clear();
        resourcePackFlags.clear();
        resourcePackEntries.clear();
        for (String macro : affectedMacros) {
            notifyChange(MacroLayer.RESOURCE_PACK, macro, "1", null);
        }
    }

    public void notifyReloadComplete() {
        for (Consumer<MacroChangeEvent> listener : changeListeners) {
            try {
                listener.accept(new MacroChangeEvent(
                        MacroLayer.GLOBAL,
                        "__RELOAD_COMPLETE__",
                        null,
                        "1",
                        Collections.emptySet()
                ));
            } catch (Exception e) {
                SketchDiagnostics.get().warn("macro-context", "Error in reload complete listener", e);
            }
        }
    }

    public enum MacroLayer {
        GLOBAL,
        RESOURCE_PACK,
        CONFIG,
        MODULE,
        TEMPLATE,
        DYNAMIC
    }

    public record MacroChangeEvent(
            MacroLayer layer,
            String macroName,
            String oldValue,
            String newValue,
            Set<KeyId> affectedShaders
    ) {
        public boolean isAddition() {
            return oldValue == null && newValue != null;
        }

        public boolean isRemoval() {
            return oldValue != null && newValue == null;
        }

        public boolean isModification() {
            return oldValue != null && newValue != null;
        }
    }
}
