package rogo.sketch.core.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.resource.loader.*;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.util.KeyId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Central resource manager for graphics resources.
 * Manages both built-in (code-controlled) and file-loaded resources.
 * 
 * Key features:
 * - Built-in resources take priority over file-loaded resources
 * - Version-based cache invalidation for ResourceReferences
 * - Platform-independent core with ResourceScanProvider for platform-specific loading
 */
public class GraphicsResourceManager {
    private static GraphicsResourceManager instance;

    // ===== Core Storage Layer =====
    private final ResourceStorage storage = new ResourceStorage();
    
    // ===== Loader System =====
    private final LoaderSystem loaders = new LoaderSystem();
    
    // ===== Reference System =====
    private final ReferenceSystem references = new ReferenceSystem();
    
    // ===== Resource Scan Provider =====
    private ResourceScanProvider scanProvider;

    protected GraphicsResourceManager() {
        registerDefaultLoaders();
    }

    public static GraphicsResourceManager getInstance() {
        if (instance == null) {
            instance = new GraphicsResourceManager();
        }
        return instance;
    }

    // ========== Public API ==========

    /**
     * Set the resource scan provider (platform-specific implementation).
     */
    public void setScanProvider(ResourceScanProvider provider) {
        this.scanProvider = provider;
    }

    /**
     * Get a resource reference (dynamic wrapper with version-based caching).
     */
    @SuppressWarnings("unchecked")
    public <T extends ResourceObject> ResourceReference<T> getReference(KeyId type, KeyId name) {
        return references.getOrCreate(type, name, () -> storage.get(type, name));
    }

    /**
     * Get a resource directly (built-in priority, then file-loaded).
     */
    public <T extends ResourceObject> T getResource(KeyId type, KeyId name) {
        return storage.get(type, name);
    }

    /**
     * Get a resource directly without inheritance (exact type match only).
     */
    public <T extends ResourceObject> T getResourceExact(KeyId type, KeyId name) {
        return storage.getExact(type, name);
    }

    /**
     * Register a built-in (code-controlled) resource.
     * Built-in resources take priority over file-loaded resources.
     */
    public void registerBuiltIn(KeyId type, KeyId name, Supplier<ResourceObject> resourceSupplier) {
        storage.registerBuiltIn(type, name, resourceSupplier);
        references.incrementVersion(type, name);
    }

    /**
     * Register a resource from JSON data.
     */
    public void registerJson(KeyId type, KeyId name, String jsonData) {
        registerJson(type, name, jsonData, null);
    }

    /**
     * Register a resource from JSON data with resource provider.
     */
    public void registerJson(KeyId type, KeyId name, String jsonData, 
                            Function<KeyId, Optional<InputStream>> resourceProvider) {
        InputStream stream = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8));
        loadAndRegister(type, name, stream, resourceProvider);
    }

    /**
     * Register a direct resource instance.
     */
    public <T extends ResourceObject> void registerDirect(KeyId type, KeyId name, T resource) {
        storage.registerLoaded(type, name, resource);
        references.incrementVersion(type, name);
        references.notifyReload(type, name, resource);
    }

    /**
     * Check if a resource exists.
     */
    public boolean hasResource(KeyId type, KeyId name) {
        return storage.get(type, name) != null;
    }

    /**
     * Remove a resource.
     */
    public void removeResource(KeyId type, KeyId name) {
        storage.remove(type, name);
        references.incrementVersion(type, name);
    }

    /**
     * Get all resources of a specific type (including inherited types).
     */
    public <T extends ResourceObject> Map<KeyId, T> getResourcesOfType(KeyId type) {
        return storage.getAllOfType(type);
    }

    /**
     * Get all resources of a specific type (exact type match only).
     */
    public <T extends ResourceObject> Map<KeyId, T> getResourcesOfTypeExact(KeyId type) {
        return storage.getAllOfTypeExact(type);
    }

    /**
     * Register a resource loader for a specific type.
     */
    public <T extends ResourceObject> void registerLoader(KeyId type, ResourceLoader<T> loader) {
        loaders.register(type, loader);
    }

    /**
     * Register a resource reload listener.
     */
    public void registerReloadListener(KeyId resourceType, KeyId resourceName, ResourceReloadListener listener) {
        references.addReloadListener(resourceType, resourceName, listener);
    }

    /**
     * Execute a full resource reload using the scan provider.
     */
    public void reload() {
        if (scanProvider == null) {
            System.err.println("No scan provider set, cannot reload resources");
            return;
        }
        
        // 1. Clear file-loaded resources (keep built-in)
        storage.clearLoaded();
        
        // 2. Load resource pack features
        loadPackFeatures();
        
        // 3. Load resources in order
        for (KeyId type : getLoadOrder()) {
            scanAndLoadType(type);
        }
        
        // 4. Notify macro system that reload is complete
        MacroContext.getInstance().notifyReloadComplete();
    }

    /**
     * Clear all resources.
     */
    public void clearAllResources() {
        storage.clearLoaded();
        references.invalidateAll();
    }

    /**
     * Dispose all resources.
     */
    public void dispose() {
        storage.dispose();
        references.clear();
    }

    /**
     * Get the sub-resource provider function.
     */
    public Function<KeyId, Optional<InputStream>> getSubResourceProvider() {
        return scanProvider != null ? scanProvider::getSubResource : id -> Optional.empty();
    }

    /**
     * Set the sub-resource provider (legacy method for compatibility).
     * @deprecated Use setScanProvider instead
     */
    @Deprecated
    public void setSubResourceProvider(Function<KeyId, Optional<InputStream>> subResourceProvider) {
        // This is a compatibility shim - create a minimal scan provider
        if (this.scanProvider == null) {
            this.scanProvider = new ResourceScanProvider() {
                @Override
                public Map<KeyId, InputStream> scanResources(KeyId resourceType) {
                    return Collections.emptyMap();
                }
                @Override
                public Optional<InputStream> getSubResource(KeyId identifier) {
                    return subResourceProvider.apply(identifier);
                }
                @Override
                public List<PackFeatureDefinition> getPackFeatures() {
                    return Collections.emptyList();
                }
            };
        }
    }

    // ========== Internal Methods ==========

    private void registerDefaultLoaders() {
        registerLoader(ResourceTypes.TEXTURE, new TextureLoader());
        registerLoader(ResourceTypes.RENDER_TARGET, new RenderTargetLoader());
        registerLoader(ResourceTypes.PARTIAL_RENDER_SETTING, new RenderSettingLoader());
        registerLoader(ResourceTypes.MESH, new MeshLoader());
        
        // New loaders for shader template system
        registerLoader(ResourceTypes.MACRO_TEMPLATE, new MacroTemplateLoader());
        registerLoader(ResourceTypes.SHADER_TEMPLATE, new ShaderTemplateLoader());
    }

    private KeyId[] getLoadOrder() {
        return new KeyId[] {
            ResourceTypes.MACRO_TEMPLATE,       // Load macro templates first
            ResourceTypes.SHADER_TEMPLATE,      // Then shader templates
            ResourceTypes.TEXTURE,
            ResourceTypes.RENDER_TARGET,
            ResourceTypes.PARTIAL_RENDER_SETTING,
            ResourceTypes.MESH,
            ResourceTypes.FUNCTION,
            ResourceTypes.DRAW_CALL
        };
    }

    private void loadPackFeatures() {
        if (scanProvider == null) return;
        
        // Clear old resource pack macros
        MacroContext.getInstance().clearResourcePackMacros();
        
        // Load new pack features
        List<PackFeatureDefinition> features = scanProvider.getPackFeatures();
        for (PackFeatureDefinition feature : features) {
            if (!feature.macros().isEmpty()) {
                MacroContext.getInstance().registerResourcePackMacros(feature.packId(), feature.macros());
            }
            if (!feature.features().isEmpty()) {
                MacroContext.getInstance().registerResourcePackFlags(feature.packId(), feature.features());
            }
        }
    }

    private void scanAndLoadType(KeyId type) {
        if (scanProvider == null) return;
        if (!loaders.hasLoader(type)) return;
        
        Map<KeyId, InputStream> resources = scanProvider.scanResources(type);
        for (Map.Entry<KeyId, InputStream> entry : resources.entrySet()) {
            loadAndRegister(type, entry.getKey(), entry.getValue(), scanProvider::getSubResource);
        }
    }

    private void loadAndRegister(KeyId type, KeyId name, InputStream stream,
                                 Function<KeyId, Optional<InputStream>> resourceProvider) {
        ResourceObject resource = loaders.load(type, name, stream, resourceProvider);
        if (resource != null) {
            storage.registerLoaded(type, name, resource);
            references.incrementVersion(type, name);
            references.notifyReload(type, name, resource);
        }
    }

    /**
     * Get detailed information about resource inheritance chain for debugging.
     */
    public String getResourceInheritanceInfo(KeyId type, KeyId name) {
        StringBuilder info = new StringBuilder();
        info.append("Resource search for: ").append(type).append(":").append(name).append("\n");

        List<KeyId> searchOrder = ResourceTypes.getSearchOrder(type);
        for (int i = 0; i < searchOrder.size(); i++) {
            KeyId searchType = searchOrder.get(i);
            boolean found = false;

            // Check built-in first (priority)
            if (storage.hasBuiltIn(searchType, name)) {
                found = true;
                info.append("  ").append(i == 0 ? "✓" : "↑").append(" Found in built-in resources of type: ")
                        .append(searchType).append(" (PRIORITY)\n");
            }

            // Then check loaded
            if (!found && storage.hasLoaded(searchType, name)) {
                found = true;
                info.append("  ").append(i == 0 ? "✓" : "↑").append(" Found in loaded resources of type: ")
                        .append(searchType).append("\n");
            }

            if (!found) {
                info.append("  ✗ Not found in type: ").append(searchType).append("\n");
            } else {
                break;
            }
        }

        return info.toString();
    }

    // ========== Internal Classes ==========

    /**
     * Resource storage layer - manages built-in and file-loaded resources.
     */
    private static class ResourceStorage {
        // File-loaded resources: Type -> (Name -> Resource)
        private final Map<KeyId, Map<KeyId, ResourceObject>> loadedResources = new ConcurrentHashMap<>();
        
        // Built-in resources: Type -> (Name -> Supplier)
        private final Map<KeyId, Map<KeyId, Supplier<ResourceObject>>> builtInResources = new ConcurrentHashMap<>();

        /**
         * Get resource (built-in priority, then file-loaded).
         */
        @SuppressWarnings("unchecked")
        <T extends ResourceObject> T get(KeyId type, KeyId name) {
            List<KeyId> searchOrder = ResourceTypes.getSearchOrder(type);
            
            for (KeyId searchType : searchOrder) {
                // 1. Check built-in first (priority)
                Supplier<ResourceObject> builtIn = getBuiltInSupplier(searchType, name);
                if (builtIn != null) {
                    ResourceObject result = builtIn.get();
                    if (result != null) return (T) result;
                }
                
                // 2. Then check file-loaded
                T loaded = getLoaded(searchType, name);
                if (loaded != null) return loaded;
            }
            
            return null;
        }

        /**
         * Get resource without inheritance (exact type match).
         */
        @SuppressWarnings("unchecked")
        <T extends ResourceObject> T getExact(KeyId type, KeyId name) {
            // 1. Check built-in first (priority)
            Supplier<ResourceObject> builtIn = getBuiltInSupplier(type, name);
            if (builtIn != null) {
                ResourceObject result = builtIn.get();
                if (result != null) return (T) result;
            }
            
            // 2. Then check file-loaded
            return getLoaded(type, name);
        }

        private Supplier<ResourceObject> getBuiltInSupplier(KeyId type, KeyId name) {
            Map<KeyId, Supplier<ResourceObject>> typeMap = builtInResources.get(type);
            return typeMap != null ? typeMap.get(name) : null;
        }

        @SuppressWarnings("unchecked")
        private <T extends ResourceObject> T getLoaded(KeyId type, KeyId name) {
            Map<KeyId, ResourceObject> typeMap = loadedResources.get(type);
            return typeMap != null ? (T) typeMap.get(name) : null;
        }

        void registerBuiltIn(KeyId type, KeyId name, Supplier<ResourceObject> supplier) {
            builtInResources.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put(name, supplier);
        }

        void registerLoaded(KeyId type, KeyId name, ResourceObject resource) {
            loadedResources.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put(name, resource);
        }

        boolean hasBuiltIn(KeyId type, KeyId name) {
            Map<KeyId, Supplier<ResourceObject>> typeMap = builtInResources.get(type);
            return typeMap != null && typeMap.containsKey(name);
        }

        boolean hasLoaded(KeyId type, KeyId name) {
            Map<KeyId, ResourceObject> typeMap = loadedResources.get(type);
            return typeMap != null && typeMap.containsKey(name);
        }

        void remove(KeyId type, KeyId name) {
            // Remove from loaded
            Map<KeyId, ResourceObject> typeResources = loadedResources.get(type);
            if (typeResources != null) {
                ResourceObject removed = typeResources.remove(name);
                if (removed != null) {
                    try {
                        removed.dispose();
                    } catch (Exception e) {
                        System.err.println("Error disposing resource: " + e.getMessage());
                    }
                }
            }
            
            // Remove from built-in
            Map<KeyId, Supplier<ResourceObject>> typeBuiltIn = builtInResources.get(type);
            if (typeBuiltIn != null) {
                typeBuiltIn.remove(name);
            }
        }

        /**
         * Clear only file-loaded resources (keep built-in).
         */
        void clearLoaded() {
            for (Map<KeyId, ResourceObject> typeMap : loadedResources.values()) {
                for (ResourceObject resource : typeMap.values()) {
                    if (resource != null) {
                        try {
                            resource.dispose();
                        } catch (Exception e) {
                            System.err.println("Error disposing resource: " + e.getMessage());
                        }
                    }
                }
            }
            loadedResources.clear();
        }

        /**
         * Dispose all resources.
         */
        void dispose() {
            clearLoaded();
            builtInResources.clear();
        }

        @SuppressWarnings("unchecked")
        <T extends ResourceObject> Map<KeyId, T> getAllOfType(KeyId type) {
            Map<KeyId, T> result = new HashMap<>();
            List<KeyId> searchOrder = ResourceTypes.getSearchOrder(type);

            for (KeyId searchType : searchOrder) {
                // Add loaded resources
                Map<KeyId, ResourceObject> typeLoaded = loadedResources.get(searchType);
                if (typeLoaded != null) {
                    for (Map.Entry<KeyId, ResourceObject> entry : typeLoaded.entrySet()) {
                        if (!result.containsKey(entry.getKey())) {
                            result.put(entry.getKey(), (T) entry.getValue());
                        }
                    }
                }

                // Add built-in resources
                Map<KeyId, Supplier<ResourceObject>> typeBuiltIn = builtInResources.get(searchType);
                if (typeBuiltIn != null) {
                    for (Map.Entry<KeyId, Supplier<ResourceObject>> entry : typeBuiltIn.entrySet()) {
                        if (!result.containsKey(entry.getKey())) {
                            try {
                                ResourceObject resource = entry.getValue().get();
                                if (resource != null) {
                                    result.put(entry.getKey(), (T) resource);
                                }
                            } catch (Exception e) {
                                System.err.println("Failed to get built-in resource: " + e.getMessage());
                            }
                        }
                    }
                }
            }

            return result;
        }

        @SuppressWarnings("unchecked")
        <T extends ResourceObject> Map<KeyId, T> getAllOfTypeExact(KeyId type) {
            Map<KeyId, T> result = new HashMap<>();

            // Add loaded resources
            Map<KeyId, ResourceObject> typeLoaded = loadedResources.get(type);
            if (typeLoaded != null) {
                for (Map.Entry<KeyId, ResourceObject> entry : typeLoaded.entrySet()) {
                    result.put(entry.getKey(), (T) entry.getValue());
                }
            }

            // Add built-in resources
            Map<KeyId, Supplier<ResourceObject>> typeBuiltIn = builtInResources.get(type);
            if (typeBuiltIn != null) {
                for (Map.Entry<KeyId, Supplier<ResourceObject>> entry : typeBuiltIn.entrySet()) {
                    if (!result.containsKey(entry.getKey())) {
                        try {
                            ResourceObject resource = entry.getValue().get();
                            if (resource != null) {
                                result.put(entry.getKey(), (T) resource);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to get built-in resource: " + e.getMessage());
                        }
                    }
                }
            }

            return result;
        }
    }

    /**
     * Loader system - manages resource loaders.
     */
    private static class LoaderSystem {
        private final Map<KeyId, Set<ResourceLoader<?>>> loaderMap = new ConcurrentHashMap<>();
        private final Gson gson = new GsonBuilder()
                .registerTypeAdapter(KeyId.class, new KeyId.GsonAdapter())
                .setPrettyPrinting()
                .create();

        void register(KeyId type, ResourceLoader<?> loader) {
            loaderMap.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(loader);
        }

        boolean hasLoader(KeyId type) {
            Set<ResourceLoader<?>> loaders = loaderMap.get(type);
            return loaders != null && !loaders.isEmpty();
        }

        ResourceObject load(KeyId type, KeyId id, InputStream stream,
                           Function<KeyId, Optional<InputStream>> subProvider) {
            Set<ResourceLoader<?>> loaders = loaderMap.get(type);
            if (loaders == null || loaders.isEmpty()) {
                System.err.println("No loader found for resource type: " + type);
                return null;
            }

            ResourceLoadContext context = new ResourceLoadContext(id, stream, gson, subProvider);

            for (ResourceLoader<?> loader : loaders) {
                try {
                    ResourceObject resource = loader.load(context);
                    if (resource != null) {
                        return resource;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load resource " + id + " of type " + type + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            return null;
        }

        Gson getGson() {
            return gson;
        }
    }

    /**
     * Reference system - manages ResourceReferences and version tracking.
     */
    private static class ReferenceSystem {
        private final Map<String, ResourceReference<?>> references = new ConcurrentHashMap<>();
        private final Map<String, Long> resourceVersions = new ConcurrentHashMap<>();
        private final Map<String, Set<ResourceReloadListener>> reloadListeners = new ConcurrentHashMap<>();

        long getVersion(KeyId type, KeyId name) {
            String key = type + ":" + name;
            return resourceVersions.getOrDefault(key, 0L);
        }

        void incrementVersion(KeyId type, KeyId name) {
            String key = type + ":" + name;
            resourceVersions.compute(key, (k, v) -> (v == null) ? 1L : v + 1);
        }

        @SuppressWarnings("unchecked")
        <T extends ResourceObject> ResourceReference<T> getOrCreate(
                KeyId type, KeyId name, Supplier<T> resolver) {
            String key = type + ":" + name;
            return (ResourceReference<T>) references.computeIfAbsent(key, k ->
                new ResourceReference<>(name, type, resolver, () -> getVersion(type, name)));
        }

        void addReloadListener(KeyId type, KeyId name, ResourceReloadListener listener) {
            String key = type + ":" + name;
            reloadListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);
        }

        void notifyReload(KeyId type, KeyId name, ResourceObject resource) {
            String key = type + ":" + name;
            Set<ResourceReloadListener> listeners = reloadListeners.get(key);
            if (listeners != null) {
                for (ResourceReloadListener listener : listeners) {
                    try {
                        listener.onResourceReload(name, resource);
                    } catch (Exception e) {
                        System.err.println("Reload listener error: " + e.getMessage());
                    }
                }
            }
        }

        void invalidateAll() {
            for (ResourceReference<?> ref : references.values()) {
                ref.invalidate();
            }
        }

        void clear() {
            references.clear();
            resourceVersions.clear();
            reloadListeners.clear();
        }
    }

    /**
     * Interface for resource reload listeners.
     */
    public interface ResourceReloadListener {
        void onResourceReload(KeyId resourceName, ResourceObject newResource);
    }
}
