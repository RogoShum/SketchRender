package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.shader.*;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.*;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enhanced shader program loader with preprocessing support
 * This extends the original loader to support the new preprocessing system
 */
public class EnhancedShaderProgramLoader implements ResourceLoader<ShaderProvider> {
    
    private final boolean usePreprocessing;
    private final ShaderPreprocessor preprocessor;
    private final ShaderResourceProvider resourceProvider;
    
    public EnhancedShaderProgramLoader(ResourceProvider minecraftResourceProvider) {
        this(minecraftResourceProvider, true);
    }
    
    public EnhancedShaderProgramLoader(ResourceProvider minecraftResourceProvider, boolean usePreprocessing) {
        this.usePreprocessing = usePreprocessing;
        if (usePreprocessing) {
            this.preprocessor = new ModernShaderPreprocessor();
            this.resourceProvider = new MinecraftShaderResourceProvider(minecraftResourceProvider);
            this.preprocessor.setResourceProvider(resourceProvider);
        } else {
            this.preprocessor = null;
            this.resourceProvider = null;
        }
    }
    
    @Override
    public ShaderProvider loadFromJson(Identifier identifier, String jsonData, Gson gson,
                                       Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);
            
            // Check for preprocessing configuration
            ShaderConfiguration config = loadConfigurationFromJson(json, identifier);
            if (config != null) {
                ShaderConfigurationManager.getInstance().setConfiguration(identifier, config);
            }
            
            if (json.has("compute")) {
                String computeSource = loadShaderSource(json.get("compute").getAsString(), resourceProvider);
                
                if (usePreprocessing) {
                    ShaderFactory factory = new ShaderFactory(this.preprocessor, this.resourceProvider);
                    return factory.createComputeShader(identifier, computeSource);
                } else {
                    return new ComputeShader(identifier, computeSource);
                }
                
            } else {
                Map<ShaderType, String> shaderSources = new HashMap<>();
                
                if (json.has("vertex")) {
                    String vertexSource = loadShaderSource(json.get("vertex").getAsString(), resourceProvider);
                    shaderSources.put(ShaderType.VERTEX, vertexSource);
                } else {
                    throw new IllegalArgumentException("Vertex shader is required");
                }
                
                if (json.has("fragment")) {
                    String fragmentSource = loadShaderSource(json.get("fragment").getAsString(), resourceProvider);
                    shaderSources.put(ShaderType.FRAGMENT, fragmentSource);
                } else {
                    throw new IllegalArgumentException("Fragment shader is required");
                }
                
                if (json.has("geometry")) {
                    String geometrySource = loadShaderSource(json.get("geometry").getAsString(), resourceProvider);
                    shaderSources.put(ShaderType.GEOMETRY, geometrySource);
                }
                
                if (json.has("tessControl") || json.has("tess_control")) {
                    JsonElement tessElement = json.has("tessControl") ? json.get("tessControl") : json.get("tess_control");
                    String tessControlSource = loadShaderSource(tessElement.getAsString(), resourceProvider);
                    shaderSources.put(ShaderType.TESS_CONTROL, tessControlSource);
                }
                
                if (json.has("tessEvaluation") || json.has("tess_evaluation")) {
                    JsonElement tessElement = json.has("tessEvaluation") ? json.get("tessEvaluation") : json.get("tess_evaluation");
                    String tessEvalSource = loadShaderSource(tessElement.getAsString(), resourceProvider);
                    shaderSources.put(ShaderType.TESS_EVALUATION, tessEvalSource);
                }
                
                if (usePreprocessing) {
                    ShaderFactory factory = new ShaderFactory(this.preprocessor, this.resourceProvider);
                    return factory.createGraphicsShader(identifier, shaderSources);
                } else {
                    return new GraphicsShader(identifier, shaderSources);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load shader program from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private ShaderConfiguration loadConfigurationFromJson(JsonObject json, Identifier identifier) {
        if (!json.has("config")) {
            return null;
        }
        
        JsonObject configJson = json.getAsJsonObject("config");
        ShaderConfiguration.Builder builder = ShaderConfiguration.builder();
        
        // Load macros/defines
        if (configJson.has("defines")) {
            JsonObject defines = configJson.getAsJsonObject("defines");
            for (Map.Entry<String, JsonElement> entry : defines.entrySet()) {
                String name = entry.getKey();
                JsonElement value = entry.getValue();
                
                if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isString()) {
                        builder.define(name, value.getAsString());
                    } else if (value.getAsJsonPrimitive().isNumber()) {
                        builder.define(name, value.getAsString());
                    } else if (value.getAsJsonPrimitive().isBoolean()) {
                        builder.define(name, value.getAsBoolean());
                    }
                } else {
                    builder.define(name); // Just enable the macro
                }
            }
        }
        
        // Load features
        if (configJson.has("features")) {
            if (configJson.get("features").isJsonArray()) {
                configJson.getAsJsonArray("features").forEach(element -> {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        builder.enableFeature(element.getAsString());
                    }
                });
            }
        }
        
        // Load properties
        if (configJson.has("properties")) {
            JsonObject properties = configJson.getAsJsonObject("properties");
            for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                
                if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isString()) {
                        builder.setProperty(key, value.getAsString());
                    } else if (value.getAsJsonPrimitive().isNumber()) {
                        builder.setProperty(key, value.getAsNumber());
                    } else if (value.getAsJsonPrimitive().isBoolean()) {
                        builder.setProperty(key, value.getAsBoolean());
                    }
                }
            }
        }
        
        // Load preset
        if (configJson.has("preset")) {
            String preset = configJson.get("preset").getAsString();
            ShaderConfiguration presetConfig = ShaderConfigurationManager.createPreset(preset);
            
            // Apply preset first, then override with specific settings
            ShaderConfiguration baseConfig = builder.build();
            ShaderConfiguration.Builder finalBuilder = ShaderConfiguration.builder();
            
            // Add preset settings
            presetConfig.getMacros().forEach(finalBuilder::define);
            presetConfig.getFeatures().forEach(finalBuilder::enableFeature);
            presetConfig.getProperties().forEach(finalBuilder::setProperty);
            
            // Override with specific settings
            baseConfig.getMacros().forEach(finalBuilder::define);
            baseConfig.getFeatures().forEach(finalBuilder::enableFeature);
            baseConfig.getProperties().forEach(finalBuilder::setProperty);
            
            return finalBuilder.build();
        }
        
        return builder.build();
    }
    
    private String loadShaderSource(String element, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        Optional<BufferedReader> reader = resourceProvider.apply(Identifier.of(element));
        if (reader.isPresent()) {
            return reader.get().lines().collect(Collectors.joining("\n"));
        }
        return "";
    }
}
