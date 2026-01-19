package rogo.sketch.render.shader.config;

import rogo.sketch.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Global manager for shader configurations with change tracking and recompilation support
 */
public class ShaderConfigurationManager {
    
    private static ShaderConfigurationManager instance;
    
    private final Map<KeyId, ShaderConfiguration> configurations = new ConcurrentHashMap<>();
    private final Map<KeyId, Set<Consumer<ShaderConfiguration>>> changeListeners = new ConcurrentHashMap<>();
    private final ShaderConfiguration globalConfiguration = new ShaderConfiguration();
    
    private ShaderConfigurationManager() {
    }
    
    public static ShaderConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (ShaderConfigurationManager.class) {
                if (instance == null) {
                    instance = new ShaderConfigurationManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Get configuration for a specific shader
     */
    public ShaderConfiguration getConfiguration(KeyId shaderId) {
        return configurations.computeIfAbsent(shaderId, id -> new ShaderConfiguration(globalConfiguration));
    }
    
    /**
     * Set configuration for a specific shader
     */
    public void setConfiguration(KeyId shaderId, ShaderConfiguration config) {
        ShaderConfiguration oldConfig = configurations.get(shaderId);
        configurations.put(shaderId, new ShaderConfiguration(config));
        
        // Notify listeners if configuration changed
        if (!Objects.equals(oldConfig, config)) {
            notifyConfigurationChanged(shaderId, config);
        }
    }
    
    /**
     * Update configuration for a specific shader
     */
    public void updateConfiguration(KeyId shaderId, Consumer<ShaderConfiguration> updater) {
        ShaderConfiguration config = getConfiguration(shaderId);
        ShaderConfiguration oldConfig = new ShaderConfiguration(config);
        
        updater.accept(config);
        
        if (!Objects.equals(oldConfig, config)) {
            notifyConfigurationChanged(shaderId, config);
        }
    }
    
    /**
     * Get the global configuration that applies to all shaders
     */
    public ShaderConfiguration getGlobalConfiguration() {
        return globalConfiguration;
    }
    
    /**
     * Update global configuration (affects all shaders)
     */
    public void updateGlobalConfiguration(Consumer<ShaderConfiguration> updater) {
        ShaderConfiguration oldConfig = new ShaderConfiguration(globalConfiguration);
        updater.accept(globalConfiguration);
        
        if (!Objects.equals(oldConfig, globalConfiguration)) {
            // Update all shader configurations and notify listeners
            for (Map.Entry<KeyId, ShaderConfiguration> entry : configurations.entrySet()) {
                KeyId shaderId = entry.getKey();
                ShaderConfiguration shaderConfig = entry.getValue();
                
                // Create new config with global changes applied
                ShaderConfiguration newConfig = new ShaderConfiguration(globalConfiguration);
                // Merge shader-specific settings
                newConfig.getMacros().putAll(shaderConfig.getMacros());
                newConfig.getFeatures().addAll(shaderConfig.getFeatures());
                newConfig.getProperties().putAll(shaderConfig.getProperties());
                
                configurations.put(shaderId, newConfig);
                notifyConfigurationChanged(shaderId, newConfig);
            }
        }
    }
    
    /**
     * Add a listener for configuration changes on a specific shader
     */
    public void addConfigurationListener(KeyId shaderId, Consumer<ShaderConfiguration> listener) {
        changeListeners.computeIfAbsent(shaderId, id -> ConcurrentHashMap.newKeySet()).add(listener);
    }
    
    /**
     * Remove a configuration listener
     */
    public void removeConfigurationListener(KeyId shaderId, Consumer<ShaderConfiguration> listener) {
        Set<Consumer<ShaderConfiguration>> listeners = changeListeners.get(shaderId);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                changeListeners.remove(shaderId);
            }
        }
    }
    
    /**
     * Remove all listeners for a shader
     */
    public void clearListeners(KeyId shaderId) {
        changeListeners.remove(shaderId);
    }
    
    /**
     * Get all shader IDs that have configurations
     */
    public Set<KeyId> getConfiguredShaders() {
        return new HashSet<>(configurations.keySet());
    }
    
    /**
     * Remove configuration for a shader
     */
    public void removeConfiguration(KeyId shaderId) {
        configurations.remove(shaderId);
        changeListeners.remove(shaderId);
    }
    
    /**
     * Clear all configurations
     */
    public void clearAll() {
        configurations.clear();
        changeListeners.clear();
        globalConfiguration.clear();
    }
    
    /**
     * Create a preset configuration with common settings
     */
    public static ShaderConfiguration createPreset(String presetName) {
        return switch (presetName.toLowerCase()) {
            case "debug" -> ShaderConfiguration.builder()
                    .define("DEBUG")
                    .define("ENABLE_VALIDATION")
                    .enableFeature("debug_output")
                    .build();
                    
            case "performance" -> ShaderConfiguration.builder()
                    .define("OPTIMIZE_PERFORMANCE")
                    .define("DISABLE_DEBUG")
                    .enableFeature("fast_math")
                    .build();
                    
            case "quality" -> ShaderConfiguration.builder()
                    .define("HIGH_QUALITY")
                    .define("ENABLE_PRECISION")
                    .enableFeature("high_precision")
                    .build();
                    
            case "compatibility" -> ShaderConfiguration.builder()
                    .define("COMPATIBILITY_MODE")
                    .define("GLSL_VERSION", "330")
                    .build();
                    
            default -> new ShaderConfiguration();
        };
    }
    
    private void notifyConfigurationChanged(KeyId shaderId, ShaderConfiguration newConfig) {
        Set<Consumer<ShaderConfiguration>> listeners = changeListeners.get(shaderId);
        if (listeners != null) {
            for (Consumer<ShaderConfiguration> listener : listeners) {
                try {
                    listener.accept(newConfig);
                } catch (Exception e) {
                    System.err.println("Error in shader configuration listener for " + shaderId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}
