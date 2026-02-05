package rogo.sketch.core.shader.config;

import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Hierarchical macro context for shader compilation.
 * Manages macros at different levels:
 * 
 * 1. Global macros - Third-party library features (e.g., ENABLE_IRIS)
 * 2. Resource pack macros - Features from loaded resource packs
 * 3. Config macros - User settings and configuration
 * 4. Dynamic flags - Runtime shader variant selection
 * 
 * Changes to any level trigger shader recompilation for affected shaders.
 */
public class MacroContext {
    private static MacroContext instance;
    
    // Macro layers (lower index = higher priority)
    private final Map<String, String> globalMacros = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> resourcePackMacros = new ConcurrentHashMap<>();
    private final Map<String, String> configMacros = new ConcurrentHashMap<>();
    
    // Flags (boolean macros, just define with value "1")
    private final Set<String> globalFlags = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> resourcePackFlags = new ConcurrentHashMap<>();
    private final Set<String> configFlags = ConcurrentHashMap.newKeySet();
    
    // Change listeners
    private final List<Consumer<MacroChangeEvent>> changeListeners = new ArrayList<>();
    
    // Shader dependencies - which shaders use which macros/flags
    private final Map<String, Set<KeyId>> macroToShaders = new ConcurrentHashMap<>();
    
    // Registered macro templates
    private final Map<KeyId, MacroTemplate> registeredTemplates = new ConcurrentHashMap<>();
    
    private MacroContext() {}
    
    public static synchronized MacroContext getInstance() {
        if (instance == null) {
            instance = new MacroContext();
        }
        return instance;
    }
    
    // ===== Global Macros (Third-party libraries) =====
    
    /**
     * Set a global macro (e.g., from a third-party library).
     * @param name Macro name
     * @param value Macro value
     */
    public void setGlobalMacro(String name, String value) {
        String oldValue = globalMacros.put(name, value);
        if (!Objects.equals(oldValue, value)) {
            notifyChange(MacroLayer.GLOBAL, name, oldValue, value);
        }
    }
    
    /**
     * Enable a global flag.
     * @param flag Flag name
     */
    public void enableGlobalFlag(String flag) {
        if (globalFlags.add(flag)) {
            notifyChange(MacroLayer.GLOBAL, flag, null, "1");
        }
    }
    
    /**
     * Disable a global flag.
     * @param flag Flag name
     */
    public void disableGlobalFlag(String flag) {
        if (globalFlags.remove(flag)) {
            notifyChange(MacroLayer.GLOBAL, flag, "1", null);
        }
    }
    
    /**
     * Remove a global macro.
     * @param name Macro name
     */
    public void removeGlobalMacro(String name) {
        String oldValue = globalMacros.remove(name);
        if (oldValue != null) {
            notifyChange(MacroLayer.GLOBAL, name, oldValue, null);
        }
    }
    
    // ===== Resource Pack Macros =====
    
    /**
     * Register macros from a resource pack.
     * @param packId Resource pack identifier
     * @param macros Map of macro name to value
     */
    public void registerResourcePackMacros(String packId, Map<String, String> macros) {
        resourcePackMacros.put(packId, new HashMap<>(macros));
        for (Map.Entry<String, String> entry : macros.entrySet()) {
            notifyChange(MacroLayer.RESOURCE_PACK, entry.getKey(), null, entry.getValue());
        }
    }
    
    /**
     * Register flags from a resource pack.
     * @param packId Resource pack identifier
     * @param flags Set of flag names
     */
    public void registerResourcePackFlags(String packId, Set<String> flags) {
        resourcePackFlags.put(packId, new HashSet<>(flags));
        for (String flag : flags) {
            notifyChange(MacroLayer.RESOURCE_PACK, flag, null, "1");
        }
    }
    
    /**
     * Unregister all macros and flags from a resource pack.
     * @param packId Resource pack identifier
     */
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
    }
    
    // ===== Config Macros (User settings) =====
    
    /**
     * Set a config macro (from user settings).
     * @param name Macro name
     * @param value Macro value
     */
    public void setConfigMacro(String name, String value) {
        String oldValue = configMacros.put(name, value);
        if (!Objects.equals(oldValue, value)) {
            notifyChange(MacroLayer.CONFIG, name, oldValue, value);
        }
    }
    
    /**
     * Enable a config flag.
     * @param flag Flag name
     */
    public void enableConfigFlag(String flag) {
        if (configFlags.add(flag)) {
            notifyChange(MacroLayer.CONFIG, flag, null, "1");
        }
    }
    
    /**
     * Disable a config flag.
     * @param flag Flag name
     */
    public void disableConfigFlag(String flag) {
        if (configFlags.remove(flag)) {
            notifyChange(MacroLayer.CONFIG, flag, "1", null);
        }
    }
    
    /**
     * Remove a config macro.
     * @param name Macro name
     */
    public void removeConfigMacro(String name) {
        String oldValue = configMacros.remove(name);
        if (oldValue != null) {
            notifyChange(MacroLayer.CONFIG, name, oldValue, null);
        }
    }
    
    // ===== Merged Macro Queries =====
    
    /**
     * Get all active macros merged from all layers.
     * Priority: Global > Resource Pack > Config
     * 
     * @return Merged macro map
     */
    public Map<String, String> getMergedMacros() {
        return getMergedMacros(Collections.emptySet());
    }
    
    /**
     * Get all active macros merged from all layers, plus dynamic flags.
     * 
     * @param dynamicFlags Additional dynamic flags for this specific variant
     * @return Merged macro map
     */
    public Map<String, String> getMergedMacros(Set<String> dynamicFlags) {
        Map<String, String> merged = new HashMap<>();
        
        // Add config macros (lowest priority)
        merged.putAll(configMacros);
        
        // Add resource pack macros
        for (Map<String, String> packMacros : resourcePackMacros.values()) {
            merged.putAll(packMacros);
        }
        
        // Add global macros (highest priority)
        merged.putAll(globalMacros);
        
        // Add all flags as "1"
        for (String flag : configFlags) {
            merged.put(flag, "1");
        }
        for (Set<String> packFlags : resourcePackFlags.values()) {
            for (String flag : packFlags) {
                merged.put(flag, "1");
            }
        }
        for (String flag : globalFlags) {
            merged.put(flag, "1");
        }
        
        // Add dynamic flags
        for (String flag : dynamicFlags) {
            merged.put(flag, "1");
        }
        
        return merged;
    }
    
    /**
     * Check if a macro/flag is defined at any level.
     * @param name Macro or flag name
     * @return true if defined
     */
    public boolean isDefined(String name) {
        if (globalMacros.containsKey(name) || globalFlags.contains(name)) {
            return true;
        }
        if (configMacros.containsKey(name) || configFlags.contains(name)) {
            return true;
        }
        for (Map<String, String> packMacros : resourcePackMacros.values()) {
            if (packMacros.containsKey(name)) {
                return true;
            }
        }
        for (Set<String> packFlags : resourcePackFlags.values()) {
            if (packFlags.contains(name)) {
                return true;
            }
        }
        return false;
    }
    
    // ===== Macro Template Management =====
    
    /**
     * Register a macro template.
     * @param id The template identifier
     * @param template The macro template
     */
    public void registerMacroTemplate(KeyId id, MacroTemplate template) {
        registeredTemplates.put(id, template);
    }
    
    /**
     * Unregister a macro template.
     * @param id The template identifier
     */
    public void unregisterMacroTemplate(KeyId id) {
        registeredTemplates.remove(id);
    }
    
    /**
     * Get a registered macro template.
     * @param id The template identifier
     * @return The template, or null if not found
     */
    public MacroTemplate getMacroTemplate(KeyId id) {
        return registeredTemplates.get(id);
    }
    
    /**
     * Get macros merged from all layers plus specified macro template IDs.
     * @param dynamicFlags Dynamic flags for this variant
     * @param templateIds Macro template IDs to include
     * @return Merged macro map
     */
    public Map<String, String> getMergedMacros(Set<String> dynamicFlags, Set<KeyId> templateIds) {
        Map<String, String> merged = getMergedMacros(dynamicFlags);
        
        // Apply macro templates
        for (KeyId templateId : templateIds) {
            MacroTemplate template = registeredTemplates.get(templateId);
            if (template != null && template.isConditionMet(this)) {
                merged = template.mergeWith(merged);
            }
        }
        
        return merged;
    }
    
    // ===== Shader Dependency Tracking =====
    
    /**
     * Register that a shader uses specific macros.
     * Used to track which shaders need recompilation when macros change.
     * 
     * @param shaderId The shader identifier
     * @param usedMacros Set of macro/flag names used by this shader
     */
    public void registerShaderDependencies(KeyId shaderId, Set<String> usedMacros) {
        for (String macro : usedMacros) {
            macroToShaders.computeIfAbsent(macro, k -> ConcurrentHashMap.newKeySet())
                         .add(shaderId);
        }
    }
    
    /**
     * Unregister shader dependencies (when shader is disposed).
     * @param shaderId The shader identifier
     */
    public void unregisterShaderDependencies(KeyId shaderId) {
        for (Set<KeyId> shaders : macroToShaders.values()) {
            shaders.remove(shaderId);
        }
    }
    
    /**
     * Get all shaders that depend on a specific macro.
     * @param macroName The macro/flag name
     * @return Set of shader identifiers
     */
    public Set<KeyId> getAffectedShaders(String macroName) {
        Set<KeyId> affected = macroToShaders.get(macroName);
        return affected != null ? new HashSet<>(affected) : Collections.emptySet();
    }
    
    // ===== Change Listeners =====
    
    /**
     * Add a listener for macro changes.
     * @param listener The listener
     */
    public void addChangeListener(Consumer<MacroChangeEvent> listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Remove a change listener.
     * @param listener The listener
     */
    public void removeChangeListener(Consumer<MacroChangeEvent> listener) {
        changeListeners.remove(listener);
    }
    
    private void notifyChange(MacroLayer layer, String name, String oldValue, String newValue) {
        MacroChangeEvent event = new MacroChangeEvent(layer, name, oldValue, newValue, 
                                                       getAffectedShaders(name));
        for (Consumer<MacroChangeEvent> listener : changeListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                System.err.println("Error in macro change listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Clear all macros and flags. Used for testing or reset.
     */
    public void clear() {
        globalMacros.clear();
        globalFlags.clear();
        resourcePackMacros.clear();
        resourcePackFlags.clear();
        configMacros.clear();
        configFlags.clear();
        macroToShaders.clear();
    }
    
    /**
     * Clear all resource pack macros and flags.
     * Called before loading new resource pack features.
     */
    public void clearResourcePackMacros() {
        // Collect all affected macros before clearing
        Set<String> affectedMacros = new HashSet<>();
        for (Map<String, String> macros : resourcePackMacros.values()) {
            affectedMacros.addAll(macros.keySet());
        }
        for (Set<String> flags : resourcePackFlags.values()) {
            affectedMacros.addAll(flags);
        }
        
        // Clear the maps
        resourcePackMacros.clear();
        resourcePackFlags.clear();
        
        // Notify about removals
        for (String macro : affectedMacros) {
            notifyChange(MacroLayer.RESOURCE_PACK, macro, "1", null);
        }
    }
    
    /**
     * Notify that a full resource reload is complete.
     * Called after all resources have been loaded.
     */
    public void notifyReloadComplete() {
        // This can be used to trigger deferred shader recompilation
        // or other post-reload cleanup tasks
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
                System.err.println("Error in reload complete listener: " + e.getMessage());
            }
        }
    }
    
    // ===== Supporting Types =====
    
    /**
     * Macro layer enumeration.
     */
    public enum MacroLayer {
        GLOBAL,         // Third-party libraries
        RESOURCE_PACK,  // Resource packs
        CONFIG,         // User configuration
        DYNAMIC         // Runtime variant selection
    }
    
    /**
     * Event fired when a macro changes.
     */
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

