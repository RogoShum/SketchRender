package rogo.sketch.core.resource.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.preprocessor.SketchShaderPreprocessor;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.shader.vertex.VertexAttributeSpec;
import rogo.sketch.core.util.KeyId;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loader for ShaderTemplate resources from JSON.
 * Creates ShaderTemplate instances that can be used to get compiled shader variants.
 * <p>
 * JSON format:
 * {
 * "vertex": "namespace:shader.vsh",
 * "fragment": "namespace:shader.fsh",
 * "geometry": "namespace:shader.gsh",           // optional
 * "attributes": ["vec3 Position", "vec2 UV0"],  // optional - explicit type format
 * // OR
 * "attributes": ["Position", "UV0", "Normal : ENABLE_NORMAL"],  // optional - type inferred from vertex shader
 * "templates": ["sketch:shadow_support"],       // optional macro templates
 * "macros": { "CUSTOM": "value" }               // optional inline macros
 * }
 * <p>
 * Attribute parsing behavior:
 * - If attribute string looks like just a name (e.g., "Position" or "Normal : MACRO"),
 *   the type will be inferred from the vertex shader source code.
 * - If attribute string includes a type (e.g., "vec3 Position"), explicit parsing is used.
 * - If type inference fails, falls back to explicit parsing.
 */
public class ShaderTemplateLoader implements ResourceLoader<ShaderTemplate> {
    private final SketchShaderPreprocessor preprocessor;

    public ShaderTemplateLoader() {
        this.preprocessor = new SketchShaderPreprocessor();
    }

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.SHADER_TEMPLATE;
    }

    @Override
    public ShaderTemplate load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null) return null;

            Function<KeyId, Optional<InputStream>> resourceProvider = context.getSubResourceProvider();
            Function<KeyId, Optional<InputStream>> shaderResourceProvider = createShaderResourceProvider(resourceProvider);

            // Load shader sources first (needed for vertex attribute type inference)
            Map<ShaderType, String> shaderSources = new EnumMap<>(ShaderType.class);
            String vertexShaderSource = null;

            if (json.has("vertex")) {
                vertexShaderSource = loadShaderSource(json.get("vertex").getAsString(), shaderResourceProvider);
                shaderSources.put(ShaderType.VERTEX, vertexShaderSource);
            }

            if (json.has("fragment")) {
                String source = loadShaderSource(json.get("fragment").getAsString(), shaderResourceProvider);
                shaderSources.put(ShaderType.FRAGMENT, source);
            }

            if (json.has("geometry")) {
                String source = loadShaderSource(json.get("geometry").getAsString(), shaderResourceProvider);
                shaderSources.put(ShaderType.GEOMETRY, source);
            }

            if (json.has("compute")) {
                String source = loadShaderSource(json.get("compute").getAsString(), shaderResourceProvider);
                shaderSources.put(ShaderType.COMPUTE, source);
            }

            if (json.has("tessControl") || json.has("tess_control")) {
                JsonElement tessElement = json.has("tessControl") ? json.get("tessControl") : json.get("tess_control");
                String source = loadShaderSource(tessElement.getAsString(), shaderResourceProvider);
                shaderSources.put(ShaderType.TESS_CONTROL, source);
            }

            if (json.has("tessEvaluation") || json.has("tess_evaluation")) {
                JsonElement tessElement = json.has("tessEvaluation") ? json.get("tessEvaluation") : json.get("tess_evaluation");
                String source = loadShaderSource(tessElement.getAsString(), shaderResourceProvider);
                shaderSources.put(ShaderType.TESS_EVALUATION, source);
            }

            // Parse vertex attributes if present (now that we have vertex shader source for type inference)
            ShaderVertexLayout vertexLayout = parseVertexLayout(json, vertexShaderSource);

            // Create shader template
            ShaderTemplate template = new ShaderTemplate(
                    keyId,
                    shaderSources,
                    vertexLayout,
                    preprocessor,
                    resourceProvider
            );

            // Add macro template associations
            if (json.has("templates") && json.get("templates").isJsonArray()) {
                JsonArray templatesArray = json.getAsJsonArray("templates");
                for (JsonElement element : templatesArray) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        template.addMacroTemplate(KeyId.of(element.getAsString()));
                    }
                }
            }

            return template;

        } catch (Exception e) {
            System.err.println("Failed to load shader template: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse vertex layout from JSON attributes array.
     * <p>
     * Tries to infer types from vertex shader source first (if only attribute names are provided).
     * Falls back to explicit type parsing (e.g., "vec3 Position") if inference fails.
     * 
     * @param json The JSON object containing the attributes array
     * @param vertexShaderSource The vertex shader source code (can be null)
     * @return The parsed ShaderVertexLayout, or null if no attributes specified
     */
    private ShaderVertexLayout parseVertexLayout(JsonObject json, String vertexShaderSource) {
        if (!json.has("attributes")) {
            return null;
        }

        JsonArray attributes = json.getAsJsonArray("attributes");
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        List<VertexAttributeSpec> specs = new ArrayList<>();
        for (int i = 0; i < attributes.size(); i++) {
            JsonElement element = attributes.get(i);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String specStr = element.getAsString();
                VertexAttributeSpec spec = null;
                
                // Try to infer type from vertex shader source first (if source is available)
                if (vertexShaderSource != null && !vertexShaderSource.isEmpty()) {
                    try {
                        // Check if this looks like just a name (no type prefix like "vec3")
                        // Pattern: word characters only, possibly with " : MACRO" suffix
                        if (specStr.matches("^\\w+(?:\\s*:\\s*\\w+)?$")) {
                            // Try to infer from source
                            spec = VertexAttributeSpec.parseFromSource(specStr, i, vertexShaderSource);
                        }
                    } catch (IllegalArgumentException e) {
                        // Inference failed, will fall back to explicit parsing
                        System.out.println("Could not infer type for attribute '" + specStr + 
                                "' from vertex shader source, trying explicit parsing: " + e.getMessage());
                    }
                }
                
                // Fall back to explicit type parsing if inference failed or not attempted
                if (spec == null) {
                    try {
                        spec = VertexAttributeSpec.parse(specStr, i);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Invalid vertex attribute spec: " + specStr + " - " + e.getMessage());
                    }
                }
                
                if (spec != null) {
                    specs.add(spec);
                }
            }
        }

        return specs.isEmpty() ? null : ShaderVertexLayout.fromParsedSpecs(specs);
    }

    /**
     * Create a shader-specific resource provider.
     */
    private Function<KeyId, Optional<InputStream>> createShaderResourceProvider(
            Function<KeyId, Optional<InputStream>> baseResourceProvider) {
        return (shaderId) -> {
            String shaderIdStr = shaderId.toString();
            String namespace, path;

            if (shaderIdStr.contains(":")) {
                String[] parts = shaderIdStr.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            } else {
                namespace = "minecraft";
                path = shaderIdStr;
            }

            KeyId shaderResourceId = KeyId.of(namespace + ":render/resource/" + ResourceTypes.SHADER_TEMPLATE + "/" + ResourceTypes.SHADER_TYPE + "/" + path);
            return baseResourceProvider.apply(shaderResourceId);
        };
    }

    private String loadShaderSource(String element, Function<KeyId, Optional<InputStream>> resourceProvider) {
        Optional<InputStream> inputStreamOpt = resourceProvider.apply(KeyId.of(element));
        if (inputStreamOpt.isPresent()) {
            try (InputStream stream = inputStreamOpt.get();
                 BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                System.err.println("Failed to read shader source: " + e.getMessage());
                return "";
            }
        }
        return "";
    }
}

