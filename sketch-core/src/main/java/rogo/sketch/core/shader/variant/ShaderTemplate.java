package rogo.sketch.core.shader.variant;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.shader.*;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.uniform.ShaderUniform;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Template for shader variants.
 * Manages source code and caches compiled variants based on macro configurations.
 * Implements ShaderProvider to be used directly in render states.
 * 
 * Usage:
 * 1. Create template with source code
 * 2. Call getVariant(flags...) to get a compiled shader for specific flags
 * 3. Template caches variants to avoid recompilation
 * 4. Macro changes automatically invalidate affected variants
 */
public class ShaderTemplate implements ResourceObject, ShaderProvider {
    private final KeyId templateId;
    private final Map<ShaderType, String> originalSources;
    private final ShaderVertexLayout vertexLayout;
    private final ShaderPreprocessor preprocessor;
    private final Function<KeyId, Optional<InputStream>> resourceProvider;
    
    // Variant info cache: variant key -> compiled shader with resources
    private final Map<ShaderVariantKey, ShaderVariantInfo> variantInfoCache = new ConcurrentHashMap<>();
    
    // Associated macro template IDs
    private final Set<KeyId> macroTemplateIds = ConcurrentHashMap.newKeySet();
    
    // Track which macros are used by this template (for invalidation)
    private final Set<String> usedMacros = ConcurrentHashMap.newKeySet();
    
    // Current active variant (for ShaderProvider interface)
    private volatile ShaderVariantKey activeVariantKey = ShaderVariantKey.EMPTY;
    
    private boolean disposed = false;
    
    /**
     * Container for shader variant information including compiled shader and resources.
     */
    public static class ShaderVariantInfo {
        private final Shader shader;
        private final Map<String, ShaderUniform<?>> uniforms;
        private final Map<KeyId, Map<KeyId, Integer>> resourceBindings;
        private final UniformHookGroup uniformHookGroup;
        
        public ShaderVariantInfo(Shader shader) {
            this.shader = shader;
            // Use ShaderResourceHelper to collect resources
            this.uniforms = ShaderResourceHelper.collectUniforms(shader.getHandle());
            this.resourceBindings = ShaderResourceHelper.discoverResourceBindings(shader.getHandle());
            this.uniformHookGroup = ShaderResourceHelper.initializeHooks(shader.getHandle(), uniforms);
        }
        
        public Shader getShader() { return shader; }
        public Map<String, ShaderUniform<?>> getUniforms() { return uniforms; }
        public Map<KeyId, Map<KeyId, Integer>> getResourceBindings() { return resourceBindings; }
        public UniformHookGroup getUniformHookGroup() { return uniformHookGroup; }
        
        public boolean isDisposed() { return shader.isDisposed(); }
        public void dispose() { shader.dispose(); }
    }
    
    /**
     * Create a new shader template.
     * 
     * @param templateId Unique identifier
     * @param sources Map of shader type to source code
     * @param vertexLayout Vertex attribute layout (can be null)
     * @param preprocessor Shader preprocessor
     * @param resourceProvider Resource provider for imports
     */
    public ShaderTemplate(KeyId templateId,
                          Map<ShaderType, String> sources,
                          ShaderVertexLayout vertexLayout,
                          ShaderPreprocessor preprocessor,
                          Function<KeyId, Optional<InputStream>> resourceProvider) {
        this.templateId = templateId;
        this.originalSources = new EnumMap<>(sources);
        this.vertexLayout = vertexLayout;
        this.preprocessor = preprocessor;
        this.resourceProvider = resourceProvider;
        
        // Register with MacroContext for change notifications
        MacroContext.getInstance().addChangeListener(this::onMacroChange);
    }
    
    /**
     * Add a macro template association.
     * @param macroTemplateId The macro template ID
     */
    public void addMacroTemplate(KeyId macroTemplateId) {
        macroTemplateIds.add(macroTemplateId);
    }
    
    /**
     * Get associated macro template IDs.
     */
    public Set<KeyId> getMacroTemplateIds() {
        return Collections.unmodifiableSet(macroTemplateIds);
    }
    
    // ===== ShaderProvider Interface =====
    
    @Override
    public int getHandle() {
        ShaderVariantInfo info = getActiveVariantInfo();
        return info != null ? info.getShader().getHandle() : 0;
    }
    
    @Override
    public KeyId getIdentifier() {
        return templateId;
    }
    
    @Override
    public UniformHookGroup getUniformHookGroup() {
        ShaderVariantInfo info = getActiveVariantInfo();
        return info != null ? info.getUniformHookGroup() : new UniformHookGroup();
    }
    
    @Override
    public Map<KeyId, Map<KeyId, Integer>> getResourceBindings() {
        ShaderVariantInfo info = getActiveVariantInfo();
        return info != null ? info.getResourceBindings() : Collections.emptyMap();
    }
    
    @Override
    public void dispose() {
        if (disposed) return;
        
        disposed = true;
        
        // Remove change listener
        MacroContext.getInstance().removeChangeListener(this::onMacroChange);
        
        // Unregister shader dependencies
        MacroContext.getInstance().unregisterShaderDependencies(templateId);
        
        // Dispose all variants
        invalidateAllVariants();
        
        usedMacros.clear();
        macroTemplateIds.clear();
    }
    
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    // ===== Variant Access =====
    
    /**
     * Get the currently active variant info.
     */
    private ShaderVariantInfo getActiveVariantInfo() {
        try {
            return getVariantInfo(activeVariantKey);
        } catch (IOException e) {
            System.err.println("Failed to get active variant: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get a compiled shader variant for the given dynamic flags.
     * 
     * @param dynamicFlags Variant-specific flags (e.g., "TRANSLUCENT", "SHADOW_PASS")
     * @return The compiled shader
     * @throws IOException if compilation fails
     */
    public Shader getVariant(String... dynamicFlags) throws IOException {
        return getVariant(ShaderVariantKey.of(dynamicFlags));
    }
    
    /**
     * Get a compiled shader variant for the given variant key.
     * 
     * @param variantKey The variant key
     * @return The compiled shader
     * @throws IOException if compilation fails
     */
    public Shader getVariant(ShaderVariantKey variantKey) throws IOException {
        return getVariantInfo(variantKey).getShader();
    }
    
    /**
     * Get the full variant info (shader + resources) for the given variant key.
     * 
     * @param variantKey The variant key
     * @return The variant info
     * @throws IOException if compilation fails
     */
    public ShaderVariantInfo getVariantInfo(ShaderVariantKey variantKey) throws IOException {
        if (disposed) {
            throw new IllegalStateException("ShaderTemplate has been disposed: " + templateId);
        }
        
        // Check cache first
        ShaderVariantInfo cached = variantInfoCache.get(variantKey);
        if (cached != null && !cached.isDisposed()) {
            return cached;
        }
        
        // Compile new variant (synchronized to prevent duplicate compilation)
        synchronized (variantInfoCache) {
            cached = variantInfoCache.get(variantKey);
            if (cached != null && !cached.isDisposed()) {
                return cached;
            }
            
            ShaderVariantInfo compiled = compileVariant(variantKey);
            variantInfoCache.put(variantKey, compiled);
            
            // Register macro dependencies
            registerMacroDependencies();
            
            return compiled;
        }
    }
    
    /**
     * Set the current active variant (used for ShaderProvider interface).
     * 
     * @param flags The variant flags
     */
    public void setActiveVariant(String... flags) {
        this.activeVariantKey = ShaderVariantKey.of(flags);
    }
    
    /**
     * Set the current active variant key.
     * 
     * @param variantKey The variant key
     */
    public void setActiveVariantKey(ShaderVariantKey variantKey) {
        this.activeVariantKey = variantKey != null ? variantKey : ShaderVariantKey.EMPTY;
    }
    
    /**
     * Get the current active variant key.
     */
    public ShaderVariantKey getActiveVariantKey() {
        return activeVariantKey;
    }
    
    /**
     * Compile a shader variant.
     */
    private ShaderVariantInfo compileVariant(ShaderVariantKey variantKey) throws IOException {
        // Get merged macros from context + dynamic flags + macro templates
        Map<String, String> macros = MacroContext.getInstance()
                .getMergedMacros(variantKey.getFlags());
        
        // Track used macros
        usedMacros.addAll(macros.keySet());
        
        // Create variant-specific shader ID
        KeyId variantId = createVariantId(variantKey);
        
        // Compile shader with macros
        Shader shader;

        if (originalSources.containsKey(ShaderType.COMPUTE)) {
            shader = new ComputeShader(variantId, originalSources.get(ShaderType.COMPUTE), preprocessor, resourceProvider, macros, vertexLayout);
        } else {
            shader = new GraphicsShader(variantId, originalSources, preprocessor, resourceProvider, macros, vertexLayout);
        }
        
        // Create variant info with resource discovery
        return new ShaderVariantInfo(shader);
    }
    
    /**
     * Create a unique ID for a variant.
     */
    private KeyId createVariantId(ShaderVariantKey variantKey) {
        if (variantKey.isEmpty()) {
            return templateId;
        }
        return KeyId.of(templateId.toString() + "#" + variantKey.toCompactString());
    }
    
    /**
     * Register macro dependencies with the macro context.
     */
    private void registerMacroDependencies() {
        if (!usedMacros.isEmpty()) {
            MacroContext.getInstance().registerShaderDependencies(templateId, usedMacros);
        }
    }
    
    /**
     * Handle macro change notifications.
     */
    private void onMacroChange(MacroContext.MacroChangeEvent event) {
        if (disposed) return;
        
        // Check if this template uses the changed macro
        if (usedMacros.contains(event.macroName())) {
            // Invalidate all variants (conservative approach)
            invalidateAllVariants();
        }
    }
    
    /**
     * Invalidate all cached variants.
     * Called when global/config macros change.
     */
    public void invalidateAllVariants() {
        synchronized (variantInfoCache) {
            for (ShaderVariantInfo info : variantInfoCache.values()) {
                if (!info.isDisposed()) {
                    info.dispose();
                }
            }
            variantInfoCache.clear();
        }
    }
    
    /**
     * Invalidate variants that use specific macros.
     * 
     * @param affectedMacros Set of macro names that changed
     */
    public void invalidateVariants(Set<String> affectedMacros) {
        // For simplicity, invalidate all if any used macro is affected
        boolean needsInvalidation = false;
        for (String macro : affectedMacros) {
            if (usedMacros.contains(macro)) {
                needsInvalidation = true;
                break;
            }
        }
        
        if (needsInvalidation) {
            invalidateAllVariants();
        }
    }
    
    // ===== Accessors =====
    
    /**
     * Get the template identifier.
     */
    public KeyId getTemplateId() {
        return templateId;
    }
    
    /**
     * Get the vertex layout for this shader.
     */
    public ShaderVertexLayout getVertexLayout() {
        return vertexLayout;
    }
    
    /**
     * Get the number of cached variants.
     */
    public int getCachedVariantCount() {
        return variantInfoCache.size();
    }
    
    /**
     * Get all cached variant keys.
     */
    public Set<ShaderVariantKey> getCachedVariantKeys() {
        return new HashSet<>(variantInfoCache.keySet());
    }
    
    /**
     * Check if a variant is cached.
     */
    public boolean hasVariant(ShaderVariantKey variantKey) {
        ShaderVariantInfo cached = variantInfoCache.get(variantKey);
        return cached != null && !cached.isDisposed();
    }
    
    @Override
    public String toString() {
        return "ShaderTemplate{" +
                "id=" + templateId +
                ", variants=" + variantInfoCache.size() +
                ", activeKey=" + activeVariantKey +
                ", disposed=" + disposed +
                '}';
    }
}
