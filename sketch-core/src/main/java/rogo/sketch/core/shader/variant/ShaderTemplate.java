package rogo.sketch.core.shader.variant;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Backend-neutral shader source template and variant specification owner.
 */
public final class ShaderTemplate implements ResourceObject {
    private final KeyId templateId;
    private final Map<ShaderType, String> originalSources;
    private final ShaderVertexLayout vertexLayout;
    private final ShaderPreprocessor preprocessor;
    private final Function<KeyId, Optional<InputStream>> resourceProvider;
    private final Map<KeyId, Map<KeyId, Integer>> declaredResourceBindings;
    private final Map<ShaderVariantKey, ShaderVariantSpec> variantSpecCache = new ConcurrentHashMap<>();
    private final Set<KeyId> macroTemplateIds = ConcurrentHashMap.newKeySet();
    private final Set<String> usedMacros = ConcurrentHashMap.newKeySet();
    private final AtomicLong variantEpoch = new AtomicLong();

    private volatile boolean disposed = false;

    public ShaderTemplate(
            KeyId templateId,
            Map<ShaderType, String> sources,
            ShaderVertexLayout vertexLayout,
            ShaderPreprocessor preprocessor,
            Function<KeyId, Optional<InputStream>> resourceProvider) {
        this(templateId, sources, vertexLayout, preprocessor, resourceProvider, Collections.emptyMap());
    }

    public ShaderTemplate(
            KeyId templateId,
            Map<ShaderType, String> sources,
            ShaderVertexLayout vertexLayout,
            ShaderPreprocessor preprocessor,
            Function<KeyId, Optional<InputStream>> resourceProvider,
            Map<KeyId, Map<KeyId, Integer>> declaredResourceBindings) {
        this.templateId = templateId;
        this.originalSources = new EnumMap<>(sources);
        this.vertexLayout = vertexLayout;
        this.preprocessor = preprocessor;
        this.resourceProvider = resourceProvider;
        this.declaredResourceBindings = immutableNestedMap(declaredResourceBindings);
        MacroContext.getInstance().addChangeListener(this::onMacroChange);
    }

    public void addMacroTemplate(KeyId macroTemplateId) {
        macroTemplateIds.add(macroTemplateId);
    }

    public Set<KeyId> getMacroTemplateIds() {
        return Collections.unmodifiableSet(macroTemplateIds);
    }

    public KeyId getIdentifier() {
        return templateId;
    }

    public boolean hasDeclaredResourceBindings() {
        return !declaredResourceBindings.isEmpty();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        MacroContext.getInstance().removeChangeListener(this::onMacroChange);
        MacroContext.getInstance().unregisterShaderDependencies(templateId);
        invalidateAllVariants();
        usedMacros.clear();
        macroTemplateIds.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public ShaderVariantSpec resolveVariantSpec(ShaderVariantKey variantKey) throws IOException {
        ShaderVariantKey normalizedVariantKey = variantKey != null ? variantKey : ShaderVariantKey.EMPTY;
        if (disposed) {
            throw new IllegalStateException("ShaderTemplate has been disposed: " + templateId);
        }

        ShaderVariantSpec cachedSpec = variantSpecCache.get(normalizedVariantKey);
        if (cachedSpec != null) {
            return cachedSpec;
        }

        synchronized (variantSpecCache) {
            cachedSpec = variantSpecCache.get(normalizedVariantKey);
            if (cachedSpec != null) {
                return cachedSpec;
            }

            Map<String, String> macros = MacroContext.getInstance().getMergedMacros(normalizedVariantKey.getFlags());
            usedMacros.addAll(macros.keySet());

            EnumMap<ShaderType, String> macroExpandedSources = new EnumMap<>(ShaderType.class);
            try {
                if (preprocessor != null && resourceProvider != null) {
                    preprocessor.setResourceProvider(
                            rogo.sketch.core.shader.preprocessor.ShaderResourceProvider.fromGenericProvider(resourceProvider));
                }
                for (Map.Entry<ShaderType, String> entry : originalSources.entrySet()) {
                    String processedSource = entry.getValue();
                    if (preprocessor != null) {
                        processedSource = preprocessor.process(entry.getValue(), templateId, macros, null).processedSource();
                    }
                    macroExpandedSources.put(entry.getKey(), processedSource);
                }
            } catch (Exception e) {
                throw new IOException("Failed to resolve shader variant spec for " + templateId + " / " + normalizedVariantKey, e);
            }

            ActiveShaderVertexLayout activeVertexLayout = ActiveShaderVertexLayout.resolve(
                    vertexLayout,
                    macroExpandedSources.get(ShaderType.VERTEX));
            EnumMap<ShaderType, String> finalSources = new EnumMap<>(macroExpandedSources);

            ShaderProgramInterfaceSpec interfaceSpec = ShaderProgramInterfaceResolver.resolve(
                    finalSources,
                    activeVertexLayout,
                    ShaderResourceBindingResolver.resolve(finalSources, declaredResourceBindings));

            ShaderVariantSpec resolvedSpec = new ShaderVariantSpec(
                    templateId,
                    normalizedVariantKey,
                    finalSources,
                    vertexLayout,
                    activeVertexLayout,
                    activeVertexLayout.activeAttributeLocations(),
                    macros.keySet(),
                    interfaceSpec);
            variantSpecCache.put(normalizedVariantKey, resolvedSpec);
            registerMacroDependencies();
            return resolvedSpec;
        }
    }

    private static Map<KeyId, Map<KeyId, Integer>> immutableNestedMap(Map<KeyId, Map<KeyId, Integer>> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<KeyId, Map<KeyId, Integer>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<KeyId, Map<KeyId, Integer>> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            copy.put(entry.getKey(), Collections.unmodifiableMap(new java.util.LinkedHashMap<>(entry.getValue())));
        }
        return copy.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(copy);
    }

    public Map<KeyId, Map<KeyId, Integer>> resolveResourceBindings(ShaderVariantKey variantKey) throws IOException {
        return resolveVariantSpec(variantKey).interfaceSpec().resourceBindings();
    }

    public long variantEpoch() {
        return variantEpoch.get();
    }

    public void invalidateAllVariants() {
        variantSpecCache.clear();
        variantEpoch.incrementAndGet();
    }

    public void invalidateVariants(Set<String> affectedMacros) {
        if (affectedMacros == null || affectedMacros.isEmpty()) {
            return;
        }
        for (String macro : affectedMacros) {
            if (usedMacros.contains(macro)) {
                invalidateAllVariants();
                return;
            }
        }
    }

    public KeyId getTemplateId() {
        return templateId;
    }

    public ShaderVertexLayout getVertexLayout() {
        return vertexLayout;
    }

    public int getCachedVariantCount() {
        return variantSpecCache.size();
    }

    public Set<ShaderVariantKey> getCachedVariantKeys() {
        return new HashSet<>(variantSpecCache.keySet());
    }

    public boolean hasVariant(ShaderVariantKey variantKey) {
        return variantSpecCache.containsKey(variantKey);
    }

    private void registerMacroDependencies() {
        if (!usedMacros.isEmpty()) {
            MacroContext.getInstance().registerShaderDependencies(templateId, usedMacros);
        }
    }

    private void onMacroChange(MacroContext.MacroChangeEvent event) {
        if (disposed) {
            return;
        }
        if (usedMacros.contains(event.macroName())) {
            invalidateAllVariants();
        }
    }

    @Override
    public String toString() {
        return "ShaderTemplate{" +
                "id=" + templateId +
                ", variants=" + variantSpecCache.size() +
                ", disposed=" + disposed +
                '}';
    }
}

