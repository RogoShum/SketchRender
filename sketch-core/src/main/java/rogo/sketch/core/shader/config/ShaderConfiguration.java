package rogo.sketch.core.shader.config;

import java.util.*;

/**
 * Configuration container for shader compilation with macro definitions and settings
 */
public class ShaderConfiguration {
    
    private final Map<String, String> macros = new HashMap<>();
    private final Set<String> features = new HashSet<>();
    private final Map<String, Object> properties = new HashMap<>();
    private int configHash = -1;
    
    public ShaderConfiguration() {
    }
    
    public ShaderConfiguration(ShaderConfiguration other) {
        this.macros.putAll(other.macros);
        this.features.addAll(other.features);
        this.properties.putAll(other.properties);
        this.configHash = other.configHash;
    }
    
    /**
     * Define a macro with a value
     */
    public ShaderConfiguration define(String name, String value) {
        macros.put(name, value);
        invalidateHash();
        return this;
    }
    
    /**
     * Define a macro as enabled (value = "1")
     */
    public ShaderConfiguration define(String name) {
        return define(name, "1");
    }
    
    /**
     * Define a macro with an integer value
     */
    public ShaderConfiguration define(String name, int value) {
        return define(name, String.valueOf(value));
    }
    
    /**
     * Define a macro with a float value
     */
    public ShaderConfiguration define(String name, float value) {
        return define(name, String.valueOf(value));
    }
    
    /**
     * Define a macro with a boolean value
     */
    public ShaderConfiguration define(String name, boolean value) {
        return define(name, value ? "1" : "0");
    }
    
    /**
     * Remove a macro definition
     */
    public ShaderConfiguration undefine(String name) {
        macros.remove(name);
        invalidateHash();
        return this;
    }
    
    /**
     * Enable a feature (adds to both macros and feature set)
     */
    public ShaderConfiguration enableFeature(String feature) {
        features.add(feature);
        define(feature.toUpperCase());
        return this;
    }
    
    /**
     * Disable a feature
     */
    public ShaderConfiguration disableFeature(String feature) {
        features.remove(feature);
        undefine(feature.toUpperCase());
        return this;
    }
    
    /**
     * Check if a feature is enabled
     */
    public boolean isFeatureEnabled(String feature) {
        return features.contains(feature);
    }
    
    /**
     * Set a configuration property
     */
    public ShaderConfiguration setProperty(String key, Object value) {
        properties.put(key, value);
        invalidateHash();
        return this;
    }
    
    /**
     * Get a configuration property
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        return (T) properties.getOrDefault(key, defaultValue);
    }
    
    /**
     * Get all macro definitions
     */
    public Map<String, String> getMacros() {
        return new HashMap<>(macros);
    }
    
    /**
     * Get all enabled features
     */
    public Set<String> getFeatures() {
        return new HashSet<>(features);
    }
    
    /**
     * Get all properties
     */
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }
    
    /**
     * Check if a macro is defined
     */
    public boolean hasMacro(String name) {
        return macros.containsKey(name);
    }
    
    /**
     * Get macro value
     */
    public String getMacro(String name) {
        return macros.get(name);
    }
    
    /**
     * Get macro value with default
     */
    public String getMacro(String name, String defaultValue) {
        return macros.getOrDefault(name, defaultValue);
    }
    
    /**
     * Clear all configuration
     */
    public ShaderConfiguration clear() {
        macros.clear();
        features.clear();
        properties.clear();
        invalidateHash();
        return this;
    }
    
    /**
     * Get a hash representing this configuration for caching purposes
     */
    public int getConfigurationHash() {
        if (configHash == -1) {
            configHash = calculateHash();
        }
        return configHash;
    }
    
    private void invalidateHash() {
        configHash = -1;
    }
    
    private int calculateHash() {
        return Objects.hash(macros, features, properties);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderConfiguration that = (ShaderConfiguration) o;
        return Objects.equals(macros, that.macros) &&
               Objects.equals(features, that.features) &&
               Objects.equals(properties, that.properties);
    }
    
    @Override
    public int hashCode() {
        return getConfigurationHash();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ShaderConfiguration{");
        
        if (!macros.isEmpty()) {
            sb.append("macros=").append(macros);
        }
        
        if (!features.isEmpty()) {
            if (sb.length() > 20) sb.append(", ");
            sb.append("features=").append(features);
        }
        
        if (!properties.isEmpty()) {
            if (sb.length() > 20) sb.append(", ");
            sb.append("properties=").append(properties.size()).append(" items");
        }
        
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Create a builder for fluent configuration
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for fluent shader configuration
     */
    public static class Builder {
        private final ShaderConfiguration config = new ShaderConfiguration();
        
        public Builder define(String name, String value) {
            config.define(name, value);
            return this;
        }
        
        public Builder define(String name) {
            config.define(name);
            return this;
        }
        
        public Builder define(String name, int value) {
            config.define(name, value);
            return this;
        }
        
        public Builder define(String name, float value) {
            config.define(name, value);
            return this;
        }
        
        public Builder define(String name, boolean value) {
            config.define(name, value);
            return this;
        }
        
        public Builder enableFeature(String feature) {
            config.enableFeature(feature);
            return this;
        }
        
        public Builder setProperty(String key, Object value) {
            config.setProperty(key, value);
            return this;
        }
        
        public ShaderConfiguration build() {
            return new ShaderConfiguration(config);
        }
    }
}
