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
 * "resourceBindings": {                         // optional - stable set 0 binding interface
 *   "texture": ["Sampler0"],                    // array shorthand auto-allocates global slots
 *   "storage_buffer": ["InputBuffer", "OutputBuffer"]
 * },
 * // Array shorthand is deterministic across all resource types, not per-type:
 * // texture entries are allocated first, then image, uniform_buffer, storage_buffer, ...
 * // so the first texture is binding 0 unless an explicit object binding already uses it.
 * // Object form can pin explicit global slots:
 * // "resourceBindings": { "texture": { "Sampler0": 0 }, "image": { "OutputImage": 1 } }
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
        Map<KeyId, Map<KeyId, Integer>> resourceBindings = parseResourceBindings(json);

        ShaderTemplate template = new ShaderTemplate(
                keyId,
                shaderSources,
                vertexLayout,
                preprocessor,
                resourceProvider,
                resourceBindings
        );

        if (json.has("templates") && json.get("templates").isJsonArray()) {
            JsonArray templatesArray = json.getAsJsonArray("templates");
            for (JsonElement element : templatesArray) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    template.addMacroTemplate(KeyId.of(element.getAsString()));
                }
            }
        }

        return template;
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

    private Map<KeyId, Map<KeyId, Integer>> parseResourceBindings(JsonObject json) {
        if (!json.has("resourceBindings") || !json.get("resourceBindings").isJsonObject()) {
            return Collections.emptyMap();
        }

        JsonObject resourceBindings = json.getAsJsonObject("resourceBindings");
        Map<KeyId, Map<KeyId, Integer>> parsed = new LinkedHashMap<>();
        Set<Integer> usedGlobalBindings = new HashSet<>();

        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(resourceBindings.entrySet());
        entries.sort((left, right) -> {
            KeyId leftType = ResourceTypes.normalize(KeyId.of(left.getKey()));
            KeyId rightType = ResourceTypes.normalize(KeyId.of(right.getKey()));
            int orderCompare = Integer.compare(resourceBindingTypeOrder(leftType), resourceBindingTypeOrder(rightType));
            if (orderCompare != 0) {
                return orderCompare;
            }
            return leftType.toString().compareTo(rightType.toString());
        });

        Map<KeyId, JsonArray> arrayBindings = new LinkedHashMap<>();
        Map<KeyId, String> sourceTypeNames = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : entries) {
            KeyId resourceType = ResourceTypes.normalize(KeyId.of(entry.getKey()));
            JsonElement bindingSpec = entry.getValue();

            if (bindingSpec.isJsonArray()) {
                arrayBindings.put(resourceType, bindingSpec.getAsJsonArray());
                sourceTypeNames.put(resourceType, entry.getKey());
            } else if (bindingSpec.isJsonObject()) {
                Map<KeyId, Integer> typeBindings = parsed.computeIfAbsent(resourceType, ignored -> new LinkedHashMap<>());
                JsonObject explicitBindings = bindingSpec.getAsJsonObject();
                for (Map.Entry<String, JsonElement> bindingEntry : explicitBindings.entrySet()) {
                    if (!bindingEntry.getValue().isJsonPrimitive() || !bindingEntry.getValue().getAsJsonPrimitive().isNumber()) {
                        throw new IllegalArgumentException("resourceBindings." + entry.getKey() + "." + bindingEntry.getKey() + " must be a number");
                    }
                    int bindingSlot = bindingEntry.getValue().getAsInt();
                    if (bindingSlot < 0) {
                        throw new IllegalArgumentException("resourceBindings." + entry.getKey() + "." + bindingEntry.getKey() + " must be >= 0");
                    }
                    if (!usedGlobalBindings.add(bindingSlot)) {
                        throw new IllegalArgumentException("resourceBindings slot " + bindingSlot + " is declared more than once in " + json);
                    }
                    typeBindings.put(KeyId.of(bindingEntry.getKey()), bindingSlot);
                }
            } else {
                throw new IllegalArgumentException("resourceBindings." + entry.getKey() + " must be an array or object");
            }
        }

        int nextGlobalBinding = 0;
        for (Map.Entry<KeyId, JsonArray> entry : arrayBindings.entrySet()) {
            KeyId resourceType = entry.getKey();
            String sourceTypeName = sourceTypeNames.getOrDefault(resourceType, resourceType.toString());
            JsonArray orderedBindings = entry.getValue();
            Map<KeyId, Integer> typeBindings = parsed.computeIfAbsent(resourceType, ignored -> new LinkedHashMap<>());
            for (int i = 0; i < orderedBindings.size(); i++) {
                JsonElement element = orderedBindings.get(i);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("resourceBindings." + sourceTypeName + "[" + i + "] must be a string");
                }
                while (usedGlobalBindings.contains(nextGlobalBinding)) {
                    nextGlobalBinding++;
                }
                typeBindings.put(KeyId.of(element.getAsString()), nextGlobalBinding);
                usedGlobalBindings.add(nextGlobalBinding);
                nextGlobalBinding++;
            }
        }

        return parsed.isEmpty() ? Collections.emptyMap() : parsed;
    }

    private int resourceBindingTypeOrder(KeyId resourceType) {
        KeyId normalized = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.TEXTURE.equals(normalized)) {
            return 0;
        }
        if (ResourceTypes.IMAGE.equals(normalized)) {
            return 1;
        }
        if (ResourceTypes.UNIFORM_BUFFER.equals(normalized)) {
            return 2;
        }
        if (ResourceTypes.STORAGE_BUFFER.equals(normalized)) {
            return 3;
        }
        if (ResourceTypes.ATOMIC_COUNTER.equals(normalized)) {
            return 4;
        }
        return 100;
    }
}


