package rogo.sketch.core.shader.config;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Macro template resource - a reusable collection of macro definitions.
 * Can be shared across multiple shaders for consistent configuration.
 * 
 * JSON format:
 * {
 *   "macros": { "SHADOW_SIZE": "2048", "MAX_LIGHTS": "16" },
 *   "flags": ["ENABLE_SHADOW", "HIGH_QUALITY"],
 *   "requires": ["ENABLE_SHADOW_MAP"]  // Prerequisites
 * }
 */
public class MacroTemplate implements ResourceObject {
    private final KeyId id;
    private final Map<String, String> macros;
    private final Set<String> flags;
    private final Set<String> requires;
    private boolean disposed = false;
    
    /**
     * Create a new macro template.
     * 
     * @param id       Template identifier
     * @param macros   Map of macro names to values
     * @param flags    Set of flag names (defined as "1")
     * @param requires Set of prerequisite macro/flag names
     */
    public MacroTemplate(KeyId id, Map<String, String> macros, Set<String> flags, Set<String> requires) {
        this.id = id;
        this.macros = Collections.unmodifiableMap(macros);
        this.flags = Collections.unmodifiableSet(flags);
        this.requires = Collections.unmodifiableSet(requires);
    }
    
    /**
     * Get the template identifier.
     */
    public KeyId getId() {
        return id;
    }
    
    /**
     * Get all macro definitions.
     */
    public Map<String, String> getMacros() {
        return macros;
    }
    
    /**
     * Get all flag names.
     */
    public Set<String> getFlags() {
        return flags;
    }
    
    /**
     * Get prerequisite macro/flag names.
     */
    public Set<String> getRequires() {
        return requires;
    }
    
    /**
     * Check if all prerequisites are met in the current macro context.
     * 
     * @param context The macro context to check against
     * @return true if all prerequisites are satisfied
     */
    public boolean isConditionMet(MacroContext context) {
        for (String required : requires) {
            if (!context.isDefined(required)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if this template defines a specific macro or flag.
     * 
     * @param name The macro or flag name
     * @return true if defined
     */
    public boolean defines(String name) {
        return macros.containsKey(name) || flags.contains(name);
    }
    
    /**
     * Get the value of a macro.
     * 
     * @param name The macro name
     * @return The value, or "1" for flags, or null if not found
     */
    public String getValue(String name) {
        String value = macros.get(name);
        if (value != null) return value;
        if (flags.contains(name)) return "1";
        return null;
    }
    
    /**
     * Merge this template's macros with a base map.
     * Template values override base values.
     * 
     * @param baseMacros The base macro map
     * @return A new map with merged values
     */
    public Map<String, String> mergeWith(Map<String, String> baseMacros) {
        java.util.HashMap<String, String> merged = new java.util.HashMap<>(baseMacros);
        merged.putAll(macros);
        for (String flag : flags) {
            merged.put(flag, "1");
        }
        return merged;
    }
    
    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        
        // Unregister from MacroContext if registered
        // The MacroContext should handle cleanup
    }
    
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    @Override
    public String toString() {
        return "MacroTemplate{" +
                "id=" + id +
                ", macros=" + macros.size() +
                ", flags=" + flags.size() +
                ", requires=" + requires +
                '}';
    }
    
    /**
     * Builder for MacroTemplate.
     */
    public static class Builder {
        private final KeyId id;
        private final java.util.HashMap<String, String> macros = new java.util.HashMap<>();
        private final java.util.HashSet<String> flags = new java.util.HashSet<>();
        private final java.util.HashSet<String> requires = new java.util.HashSet<>();
        
        public Builder(KeyId id) {
            this.id = id;
        }
        
        public Builder macro(String name, String value) {
            macros.put(name, value);
            return this;
        }
        
        public Builder flag(String name) {
            flags.add(name);
            return this;
        }
        
        public Builder require(String name) {
            requires.add(name);
            return this;
        }
        
        public MacroTemplate build() {
            return new MacroTemplate(id, macros, flags, requires);
        }
    }
    
    public static Builder builder(KeyId id) {
        return new Builder(id);
    }
}

