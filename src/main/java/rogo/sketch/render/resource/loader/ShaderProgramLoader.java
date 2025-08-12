package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.render.shader.ComputeShaderProgram;
import rogo.sketch.render.shader.GraphicsShaderProgram;
import rogo.sketch.render.shader.Shader;
import rogo.sketch.render.shader.ShaderType;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loader for ShaderProgram resources from JSON
 */
public class ShaderProgramLoader implements ResourceLoader<Shader> {

    @Override
    public Shader loadFromJson(Identifier identifier, String jsonData, Gson gson, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            if (json.has("compute")) {
                String compute = json.get("compute").getAsString();

                return new ComputeShaderProgram(identifier, loadShaderSource(compute, resourceProvider));
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

                return new GraphicsShaderProgram(identifier, shaderSources);
            }
        } catch (Exception e) {
            System.err.println("Failed to load shader program from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String loadShaderSource(String element, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        Optional<BufferedReader> reader = resourceProvider.apply(Identifier.of(element));
        if (reader.isPresent()) {
            return reader.get().lines().collect(Collectors.joining("\n"));
        }

        return "";
    }
}