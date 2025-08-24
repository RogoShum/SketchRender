package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * Loader for ShaderProgram resources from JSON
 * Supports both basic shader loading and enhanced preprocessing
 */
public class ShaderProgramLoader implements ResourceLoader<ShaderProvider> {
    
    private final boolean usePreprocessing;
    private final ShaderPreprocessor preprocessor;
    
    public ShaderProgramLoader() {
        this(true); // Always use preprocessing by default
    }
    
    public ShaderProgramLoader(boolean usePreprocessing) {
        this.usePreprocessing = usePreprocessing;
        // Always create a preprocessor - it's needed for imports and macros
        this.preprocessor = new ModernShaderPreprocessor();
    }

    @Override
    public ShaderProvider loadFromJson(Identifier identifier, String jsonData, Gson gson, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);
            
            // Load shader configuration if present
            ShaderConfiguration config = loadConfigurationFromJson(json, identifier);
            if (config != null) {
                ShaderConfigurationManager.getInstance().setConfiguration(identifier, config);
            }
            
            // Create sub-resource provider for shader files
            Function<Identifier, Optional<BufferedReader>> shaderResourceProvider = createShaderResourceProvider(resourceProvider);

            if (json.has("compute")) {
                String computeSource = loadShaderSource(json.get("compute").getAsString(), shaderResourceProvider);
                
                // Always use preprocessing for imports and macros
                // Pass the base resourceProvider (not shaderResourceProvider) for import resolution
                return ComputeShader.reloadable(identifier, computeSource, preprocessor, resourceProvider);
                
            } else {
                Map<ShaderType, String> shaderSources = new HashMap<>();

                if (json.has("vertex")) {
                    String vertexSource = loadShaderSource(json.get("vertex").getAsString(), shaderResourceProvider);
                    shaderSources.put(ShaderType.VERTEX, vertexSource);
                } else {
                    throw new IllegalArgumentException("Vertex shader is required");
                }

                if (json.has("fragment")) {
                    String fragmentSource = loadShaderSource(json.get("fragment").getAsString(), shaderResourceProvider);
                    shaderSources.put(ShaderType.FRAGMENT, fragmentSource);
                } else {
                    throw new IllegalArgumentException("Fragment shader is required");
                }

                if (json.has("geometry")) {
                    String geometrySource = loadShaderSource(json.get("geometry").getAsString(), shaderResourceProvider);
                    shaderSources.put(ShaderType.GEOMETRY, geometrySource);
                }

                if (json.has("tessControl") || json.has("tess_control")) {
                    JsonElement tessElement = json.has("tessControl") ? json.get("tessControl") : json.get("tess_control");
                    String tessControlSource = loadShaderSource(tessElement.getAsString(), shaderResourceProvider);
                    shaderSources.put(ShaderType.TESS_CONTROL, tessControlSource);
                }

                if (json.has("tessEvaluation") || json.has("tess_evaluation")) {
                    JsonElement tessElement = json.has("tessEvaluation") ? json.get("tessEvaluation") : json.get("tess_evaluation");
                    String tessEvalSource = loadShaderSource(tessElement.getAsString(), shaderResourceProvider);
                    shaderSources.put(ShaderType.TESS_EVALUATION, tessEvalSource);
                }

                // Always use preprocessing for imports and macros
                // Pass the base resourceProvider (not shaderResourceProvider) for import resolution
                return GraphicsShader.reloadable(identifier, shaderSources, preprocessor, resourceProvider);
            }
        } catch (Exception e) {
            System.err.println("Failed to load shader program from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Create a shader-specific resource provider that maps shader identifiers to shader_type paths
     * This is specifically for loading main shader files (vertex.glsl, fragment.glsl, etc.)
     * Import files are handled separately by the ShaderResourceProvider adapter
     */
    private Function<Identifier, Optional<BufferedReader>> createShaderResourceProvider(Function<Identifier, Optional<BufferedReader>> baseResourceProvider) {
        return (shaderId) -> {
            // Parse namespace:path format
            String shaderIdStr = shaderId.toString();
            String namespace, path;
            
            if (shaderIdStr.contains(":")) {
                String[] parts = shaderIdStr.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            } else {
                namespace = "minecraft"; // default namespace
                path = shaderIdStr;
            }
            
            // Create shader-specific path: namespace:render/shader_type/path
            // This is for main shader files (vertex, fragment, etc.)
            Identifier shaderResourceId = Identifier.of(namespace + ":render/shader_type/" + path);
            return baseResourceProvider.apply(shaderResourceId);
        };
    }
    
    private String loadShaderSource(String element, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        Optional<BufferedReader> reader = resourceProvider.apply(Identifier.of(element));
        if (reader.isPresent()) {
            try (BufferedReader br = reader.get()) {
                return br.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                System.err.println("Failed to read shader source: " + e.getMessage());
                return "";
            }
        }
        return "";
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
}