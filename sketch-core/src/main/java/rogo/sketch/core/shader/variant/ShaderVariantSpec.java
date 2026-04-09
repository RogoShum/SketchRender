package rogo.sketch.core.shader.variant;

import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Backend-neutral resolved shader variant.
 * <p>
 * This is the stable hand-off object between shader templates and backend-
 * specific compilation paths.
 */
public final class ShaderVariantSpec {
    private final KeyId templateId;
    private final ShaderVariantKey variantKey;
    private final Map<ShaderType, String> processedSources;
    private final ShaderVertexLayout declaredVertexLayout;
    private final ActiveShaderVertexLayout activeVertexLayout;
    private final Map<String, Integer> activeAttributeLocations;
    private final Set<String> activeMacros;
    private final ShaderProgramInterfaceSpec interfaceSpec;

    public ShaderVariantSpec(
            KeyId templateId,
            ShaderVariantKey variantKey,
            Map<ShaderType, String> processedSources,
            ShaderVertexLayout declaredVertexLayout,
            ActiveShaderVertexLayout activeVertexLayout,
            Map<String, Integer> activeAttributeLocations,
            Set<String> activeMacros,
            ShaderProgramInterfaceSpec interfaceSpec) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.variantKey = variantKey != null ? variantKey : ShaderVariantKey.EMPTY;
        EnumMap<ShaderType, String> sourceCopy = new EnumMap<>(ShaderType.class);
        if (processedSources != null) {
            sourceCopy.putAll(processedSources);
        }
        this.processedSources = Collections.unmodifiableMap(sourceCopy);
        this.declaredVertexLayout = declaredVertexLayout != null ? declaredVertexLayout : ShaderVertexLayout.empty();
        this.activeVertexLayout = activeVertexLayout != null ? activeVertexLayout : ActiveShaderVertexLayout.empty();
        this.activeAttributeLocations = activeAttributeLocations != null
                ? Collections.unmodifiableMap(activeAttributeLocations)
                : Collections.emptyMap();
        this.activeMacros = activeMacros != null ? Set.copyOf(activeMacros) : Set.of();
        this.interfaceSpec = interfaceSpec != null ? interfaceSpec : ShaderProgramInterfaceSpec.empty();
    }

    public KeyId templateId() {
        return templateId;
    }

    public ShaderVariantKey variantKey() {
        return variantKey;
    }

    public Map<ShaderType, String> processedSources() {
        return processedSources;
    }

    public ShaderVertexLayout declaredVertexLayout() {
        return declaredVertexLayout;
    }

    public ActiveShaderVertexLayout activeVertexLayout() {
        return activeVertexLayout;
    }

    public Map<String, Integer> activeAttributeLocations() {
        return activeAttributeLocations;
    }

    public Set<String> activeMacros() {
        return activeMacros;
    }

    public ShaderProgramInterfaceSpec interfaceSpec() {
        return interfaceSpec;
    }

    public Map<KeyId, Map<KeyId, Integer>> resourceBindings() {
        return interfaceSpec.resourceBindings();
    }
}

