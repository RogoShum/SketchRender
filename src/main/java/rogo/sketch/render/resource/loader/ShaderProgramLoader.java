package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.render.shader.GraphicsShaderProgram;
import rogo.sketch.render.shader.ShaderType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loader for ShaderProgram resources from JSON
 */
public class ShaderProgramLoader implements ResourceLoader<GraphicsShaderProgram> {

    @Override
    public GraphicsShaderProgram loadFromJson(String jsonData, Gson gson) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            String identifier = json.get("identifier").getAsString();
            
            // Parse shader sources
            Map<ShaderType, String> shaderSources = new HashMap<>();
            
            // Load vertex shader (required)
            if (json.has("vertex")) {
                String vertexSource = loadShaderSource(json.get("vertex"));
                shaderSources.put(ShaderType.VERTEX, vertexSource);
            } else {
                throw new IllegalArgumentException("Vertex shader is required");
            }
            
            // Load fragment shader (required)
            if (json.has("fragment")) {
                String fragmentSource = loadShaderSource(json.get("fragment"));
                shaderSources.put(ShaderType.FRAGMENT, fragmentSource);
            } else {
                throw new IllegalArgumentException("Fragment shader is required");
            }
            
            // Load optional geometry shader
            if (json.has("geometry")) {
                String geometrySource = loadShaderSource(json.get("geometry"));
                shaderSources.put(ShaderType.GEOMETRY, geometrySource);
            }
            
            // Load optional tessellation control shader
            if (json.has("tessControl") || json.has("tess_control")) {
                JsonElement tessElement = json.has("tessControl") ? json.get("tessControl") : json.get("tess_control");
                String tessControlSource = loadShaderSource(tessElement);
                shaderSources.put(ShaderType.TESS_CONTROL, tessControlSource);
            }
            
            // Load optional tessellation evaluation shader
            if (json.has("tessEvaluation") || json.has("tess_evaluation")) {
                JsonElement tessElement = json.has("tessEvaluation") ? json.get("tessEvaluation") : json.get("tess_evaluation");
                String tessEvalSource = loadShaderSource(tessElement);
                shaderSources.put(ShaderType.TESS_EVALUATION, tessEvalSource);
            }
            
            // Create shader program
            return new GraphicsShaderProgram(identifier, shaderSources);

        } catch (Exception e) {
            System.err.println("Failed to load shader program from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load shader source from JSON element (can be file path or inline source)
     */
    private String loadShaderSource(JsonElement element) throws IOException {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            
            // Check if it's a file path or inline source
            if (value.startsWith("file:")) {
                // Load from file
                String filePath = value.substring(5); // Remove "file:" prefix
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    throw new IOException("Shader file not found: " + filePath);
                }
                return Files.readString(path);
            } else if (value.contains("\n") || value.contains("void main")) {
                // Treat as inline source code
                return value;
            } else {
                // Treat as file path without prefix
                Path path = Paths.get(value);
                if (Files.exists(path)) {
                    return Files.readString(path);
                } else {
                    // Assume it's inline source if file doesn't exist
                    return value;
                }
            }
        } else if (element.isJsonObject()) {
            JsonObject sourceObj = element.getAsJsonObject();
            
            if (sourceObj.has("file")) {
                // Load from file
                String filePath = sourceObj.get("file").getAsString();
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    throw new IOException("Shader file not found: " + filePath);
                }
                return Files.readString(path);
            } else if (sourceObj.has("source")) {
                // Inline source
                return sourceObj.get("source").getAsString();
            } else {
                throw new IllegalArgumentException("Shader source object must have 'file' or 'source' field");
            }
        } else {
            throw new IllegalArgumentException("Invalid shader source: must be string or object");
        }
    }

    @Override
    public Class<GraphicsShaderProgram> getResourceClass() {
        return GraphicsShaderProgram.class;
    }
} 