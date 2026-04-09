package rogo.sketch.core.shader.variant;

import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Backend-neutral runtime interface description for a shader program variant.
 */
public final class ShaderProgramInterfaceSpec {
    private static final ShaderProgramInterfaceSpec EMPTY = new ShaderProgramInterfaceSpec(
            ActiveShaderVertexLayout.empty(),
            Collections.emptyMap(),
            ShaderUniformSchema.empty());

    private final ActiveShaderVertexLayout activeVertexLayout;
    private final Map<KeyId, Map<KeyId, Integer>> resourceBindings;
    private final ShaderUniformSchema uniformSchema;

    public ShaderProgramInterfaceSpec(
            ActiveShaderVertexLayout activeVertexLayout,
            Map<KeyId, Map<KeyId, Integer>> resourceBindings,
            ShaderUniformSchema uniformSchema) {
        this.activeVertexLayout = activeVertexLayout != null ? activeVertexLayout : ActiveShaderVertexLayout.empty();
        this.resourceBindings = immutableNestedMap(resourceBindings);
        this.uniformSchema = uniformSchema != null ? uniformSchema : ShaderUniformSchema.empty();
    }

    public static ShaderProgramInterfaceSpec empty() {
        return EMPTY;
    }

    public ActiveShaderVertexLayout activeVertexLayout() {
        return activeVertexLayout;
    }

    public Map<KeyId, Map<KeyId, Integer>> resourceBindings() {
        return resourceBindings;
    }

    public ShaderUniformSchema uniformSchema() {
        return uniformSchema;
    }

    private static Map<KeyId, Map<KeyId, Integer>> immutableNestedMap(Map<KeyId, Map<KeyId, Integer>> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<KeyId, Map<KeyId, Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<KeyId, Map<KeyId, Integer>> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return copy.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(copy);
    }
}

