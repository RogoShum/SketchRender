package rogo.sketch.render.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.loader.*;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public class GraphicsResourceManager {
    private static GraphicsResourceManager instance;

    // Resource storage: Type -> (Name -> Resource)
    private final Map<Identifier, Map<Identifier, ResourceObject>> resources = new ConcurrentHashMap<>();

    // Manual (code-controlled) resources: Type -> (Name -> Supplier)
    private final Map<Identifier, Map<Identifier, Supplier<Optional<ResourceObject>>>> mutableResources = new ConcurrentHashMap<>();

    // Resource references for external use
    private final Map<String, ResourceReference<?>> references = new ConcurrentHashMap<>();

    // Resource loaders for different types
    private final ResourceLoaderRegistry loaderRegistry = new ResourceLoaderRegistry();

    private final Map<Identifier, Map<Identifier, Set<ResourceReloadListener>>> reloadListeners = new ConcurrentHashMap<>();

    private Function<Identifier, Optional<BufferedReader>> subResourceProvider;

    // JSON processor
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new Identifier.GsonAdapter())
            .setPrettyPrinting()
            .create();

    protected GraphicsResourceManager() {
        registerDefaultLoaders();
    }

    public static GraphicsResourceManager getInstance() {
        if (instance == null) {
            instance = new GraphicsResourceManager();
        }
        return instance;
    }

    /**
     * Register a manual (code-controlled) resource
     */
    public void registerMutable(Identifier type, Identifier name, Supplier<Optional<ResourceObject>> resourceSupplier) {
        mutableResources.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .put(name, resourceSupplier);

        // Invalidate existing references
        invalidateReferences(type, name);
    }

    /**
     * Register a resource from JSON data
     * Note: This method directly loads and stores the resource without caching JSON
     */
    public void registerJson(Identifier type, Identifier name, String jsonData) {
        registerJson(type, name, jsonData, null);
    }

    /**
     * Register a resource from JSON data with resource provider
     * Enhanced to properly handle reload listeners during resource reload cycles
     */
    public void registerJson(Identifier type, Identifier name, String jsonData, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        // Check if there's an existing reload listener for this resource
        Set<ResourceReloadListener> existingListener = getReloadListener(type, name);

        // Use the provided resource provider or fall back to the global sub-resource provider
        Function<Identifier, Optional<BufferedReader>> actualProvider = resourceProvider != null ? resourceProvider : subResourceProvider;

        // Load the new resource
        loadJsonResource(type, name, jsonData, actualProvider);
        invalidateReferences(type, name);

        // Get the newly loaded resource
        ResourceObject newResource = getResourceExact(type, name).orElse(null);

        // Always notify reload listeners if they exist
        // This ensures proper update handling during resource reloads
        if (newResource != null && existingListener != null) {
            try {
                for (ResourceReloadListener listener : existingListener) {
                    listener.onResourceReload(name, newResource);
                }
            } catch (Exception e) {
                System.err.println("Error in resource reload listener for " + type + ":" + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Register a direct resource instance
     */
    public <T extends ResourceObject> void registerDirect(Identifier type, Identifier name, T resource) {
        resources.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .put(name, resource);

        // Invalidate existing references
        invalidateReferences(type, name);
    }

    /**
     * Get a resource reference (safe wrapper)
     */
    @SuppressWarnings("unchecked")
    public <T extends ResourceObject> ResourceReference<T> getReference(Identifier type, Identifier name) {
        String key = type + ":" + name;
        return (ResourceReference<T>) references.computeIfAbsent(key, k -> {
            List<Identifier> searchOrder = ResourceTypes.getSearchOrder(type);

            for (Identifier searchType : searchOrder) {
                Map<Identifier, ResourceObject> typeResources = resources.get(searchType);
                if (typeResources != null) {
                    ResourceObject resource = typeResources.get(name);
                    if (resource != null) {
                        return new ResourceReference<>(name, type, () -> getResource(type, name));
                    }
                }

                Map<Identifier, Supplier<Optional<ResourceObject>>> typeManual = mutableResources.get(searchType);
                if (typeManual != null) {
                    Supplier<Optional<ResourceObject>> supplier = typeManual.get(name);
                    if (supplier != null) {
                        return new ResourceReference<>(name, type, supplier, true);
                    }
                }
            }

            return new ResourceReference<>(name, type, () -> getResource(type, name));
        });
    }

    /**
     * Get a resource directly with inheritance support
     * Searches the requested type first, then searches parent types in the inheritance chain
     */
    @SuppressWarnings("unchecked")
    public <T extends ResourceObject> Optional<T> getResource(Identifier type, Identifier name) {
        // Get the search order based on inheritance hierarchy
        List<Identifier> searchOrder = ResourceTypes.getSearchOrder(type);

        for (Identifier searchType : searchOrder) {
            // Try direct resources first
            Map<Identifier, ResourceObject> typeResources = resources.get(searchType);
            if (typeResources != null) {
                ResourceObject resource = typeResources.get(name);
                if (resource != null) {
                    return Optional.of((T) resource);
                }
            }

            // Try manual resources
            Map<Identifier, Supplier<Optional<ResourceObject>>> typeManual = mutableResources.get(searchType);
            if (typeManual != null) {
                Supplier<Optional<ResourceObject>> supplier = typeManual.get(name);
                if (supplier != null) {
                    try {
                        Optional<ResourceObject> resource = supplier.get();
                        return (Optional<T>) resource;
                    } catch (Exception e) {
                        System.err.println("Failed to create mutable resource " + name + " of type " + searchType + ": " + e.getMessage());
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Get a resource directly without inheritance (exact type match only)
     * Useful when you specifically need a resource of the exact type
     */
    @SuppressWarnings("unchecked")
    public <T extends ResourceObject> Optional<T> getResourceExact(Identifier type, Identifier name) {
        // Try direct resources first
        Map<Identifier, ResourceObject> typeResources = resources.get(type);
        if (typeResources != null) {
            ResourceObject resource = typeResources.get(name);
            if (resource != null) {
                return Optional.of((T) resource);
            }
        }

        // Try manual resources
        Map<Identifier, Supplier<Optional<ResourceObject>>> typeManual = mutableResources.get(type);
        if (typeManual != null) {
            Supplier<Optional<ResourceObject>> supplier = typeManual.get(name);
            if (supplier != null) {
                try {
                    return (Optional<T>) supplier.get();
                } catch (Exception e) {
                    System.err.println("Failed to create manual resource " + name + " of type " + type + ": " + e.getMessage());
                }
            }
        }

        return Optional.empty();
    }

    public void clearAllResources() {
        for (Map<Identifier, ResourceObject> typeResources : resources.values()) {
            for (ResourceObject resource : typeResources.values()) {
                if (resource != null) {
                    try {
                        resource.dispose();
                    } catch (Exception e) {
                        System.err.println("Error disposing resource: " + e.getMessage());
                    }
                }
            }
        }
        resources.clear();

        for (ResourceReference<?> ref : references.values()) {
            ref.invalidate();
        }
    }

    /**
     * Load a JSON resource
     */
    private void loadJsonResource(Identifier type, Identifier identifier, String jsonData, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        Set<ResourceLoader<ResourceObject>> loader = loaderRegistry.getLoader(type);
        if (loader == null) {
            System.err.println("No loader found for resource type: " + type);
            return;
        }

        try {
            for (ResourceLoader<ResourceObject> resourceLoader : loader) {
                ResourceObject resource = resourceLoader.loadFromJson(identifier, jsonData, gson, resourceProvider);
                if (resource != null) {
                    resources.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                            .put(identifier, resource);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load JSON resource " + identifier + " of type " + type + ": " + e.getMessage());
        }
    }

    /**
     * Invalidate references for a specific resource
     */
    private void invalidateReferences(Identifier type, Identifier name) {
        String key = type + ":" + name;
        ResourceReference<?> ref = references.get(key);
        if (ref != null) {
            ref.invalidate();
        }
    }

    /**
     * Register a resource loader for a specific type
     */
    public <T extends ResourceObject> void registerLoader(Identifier type, ResourceLoader<T> loader) {
        loaderRegistry.registerLoader(type, loader);
    }

    /**
     * Get all resources of a specific type (including inherited types)
     */
    public <T extends ResourceObject> Map<Identifier, T> getResourcesOfType(Identifier type) {
        Map<Identifier, T> result = new HashMap<>();

        // Get all types to search (including inheritance chain)
        List<Identifier> searchOrder = ResourceTypes.getSearchOrder(type);

        for (Identifier searchType : searchOrder) {
            // Add direct resources
            Map<Identifier, ResourceObject> typeResources = resources.get(searchType);
            if (typeResources != null) {
                for (Map.Entry<Identifier, ResourceObject> entry : typeResources.entrySet()) {
                    // Only add if not already present (child types take precedence)
                    if (!result.containsKey(entry.getKey())) {
                        @SuppressWarnings("unchecked")
                        T resource = (T) entry.getValue();
                        result.put(entry.getKey(), resource);
                    }
                }
            }

            // Add manual resources (instantiate them)
            Map<Identifier, Supplier<Optional<ResourceObject>>> typeManual = mutableResources.get(searchType);
            if (typeManual != null) {
                for (Map.Entry<Identifier, Supplier<Optional<ResourceObject>>> entry : typeManual.entrySet()) {
                    // Only add if not already present (child types take precedence)
                    if (!result.containsKey(entry.getKey())) {
                        try {
                            @SuppressWarnings("unchecked")
                            Optional<T> resource = (Optional<T>) entry.getValue().get();
                            if (resource.isPresent()) {
                                result.put(entry.getKey(), resource.get());
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to instantiate manual resource: " + e.getMessage());
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Get all resources of a specific type (exact type match only)
     */
    public <T extends ResourceObject> Map<Identifier, T> getResourcesOfTypeExact(Identifier type) {
        Map<Identifier, T> result = new HashMap<>();

        // Add direct resources
        Map<Identifier, ResourceObject> typeResources = resources.get(type);
        if (typeResources != null) {
            for (Map.Entry<Identifier, ResourceObject> entry : typeResources.entrySet()) {
                @SuppressWarnings("unchecked")
                T resource = (T) entry.getValue();
                result.put(entry.getKey(), resource);
            }
        }

        // Add manual resources (instantiate them)
        Map<Identifier, Supplier<Optional<ResourceObject>>> typeManual = mutableResources.get(type);
        if (typeManual != null) {
            for (Map.Entry<Identifier, Supplier<Optional<ResourceObject>>> entry : typeManual.entrySet()) {
                try {
                    @SuppressWarnings("unchecked")
                    Optional<T> resource = (Optional<T>) entry.getValue().get();
                    if (resource.isPresent()) {
                        result.put(entry.getKey(), resource.get());
                    }
                } catch (Exception e) {
                    System.err.println("Failed to instantiate manual resource: " + e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Check if a resource exists
     */
    public boolean hasResource(Identifier type, Identifier name) {
        return getResource(type, name).isPresent();
    }

    /**
     * Remove a resource
     */
    public void removeResource(Identifier type, Identifier name) {
        // Remove from direct resources
        Map<Identifier, ResourceObject> typeResources = resources.get(type);
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

        // Remove from manual resources
        Map<Identifier, Supplier<Optional<ResourceObject>>> typeManual = mutableResources.get(type);
        if (typeManual != null) {
            typeManual.remove(name);
        }

        // Invalidate reference
        invalidateReferences(type, name);
    }

    /**
     * Dispose all resources
     */
    public void dispose() {
        for (Map<Identifier, ResourceObject> typeResources : resources.values()) {
            for (ResourceObject resource : typeResources.values()) {
                if (resource != null) {
                    try {
                        resource.dispose();
                    } catch (Exception e) {
                        System.err.println("Error disposing resource: " + e.getMessage());
                    }
                }
            }
        }

        resources.clear();
        mutableResources.clear();
        references.clear();
    }

    /**
     * Get detailed information about resource inheritance chain for debugging
     */
    public String getResourceInheritanceInfo(Identifier type, Identifier name) {
        StringBuilder info = new StringBuilder();
        info.append("Resource search for: ").append(type).append(":").append(name).append("\n");

        List<Identifier> searchOrder = ResourceTypes.getSearchOrder(type);
        for (int i = 0; i < searchOrder.size(); i++) {
            Identifier searchType = searchOrder.get(i);
            boolean found = false;

            // Check direct resources
            Map<Identifier, ResourceObject> typeResources = resources.get(searchType);
            if (typeResources != null && typeResources.containsKey(name)) {
                found = true;
                info.append("  ").append(i == 0 ? "✓" : "↑").append(" Found in direct resources of type: ").append(searchType).append("\n");
            }

            // Check manual resources
            Map<Identifier, Supplier<Optional<ResourceObject>>> typeManual = mutableResources.get(searchType);
            if (!found && typeManual != null && typeManual.containsKey(name)) {
                found = true;
                info.append("  ").append(i == 0 ? "✓" : "↑").append(" Found in manual resources of type: ").append(searchType).append("\n");
            }

            if (!found) {
                info.append("  ✗ Not found in type: ").append(searchType).append("\n");
            } else {
                break; // Stop at first match
            }
        }

        return info.toString();
    }

    /**
     * Register default resource loaders
     */
    private void registerDefaultLoaders() {
        registerLoader(ResourceTypes.TEXTURE, new TextureLoader());
        registerLoader(ResourceTypes.RENDER_TARGET, new RenderTargetLoader());
        registerLoader(ResourceTypes.SHADER_PROGRAM, new ShaderProgramLoader());
        registerLoader(ResourceTypes.PARTIAL_RENDER_SETTING, new RenderSettingLoader());
    }

    /**
     * Get the current sub-resource provider
     */
    public Function<Identifier, Optional<BufferedReader>> getSubResourceProvider() {
        return subResourceProvider;
    }

    /**
     * Set the sub-resource provider for loading child resources
     * This allows resource loaders to load additional files they need
     */
    public void setSubResourceProvider(Function<Identifier, Optional<BufferedReader>> subResourceProvider) {
        this.subResourceProvider = subResourceProvider;
    }

    /**
     * Register a resource reload listener for automatic updates
     * Uses double buffering - new registrations go to the register buffer
     */
    public void registerReloadListener(Identifier resourceType, Identifier resourceName, ResourceReloadListener listener) {
        reloadListeners
                .computeIfAbsent(resourceType, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(resourceName, k -> ConcurrentHashMap.newKeySet())
                .add(listener);
    }

    private void removeReloadListener(Identifier resourceType, Identifier resourceName, ResourceReloadListener listener) {
        Map<Identifier, Set<ResourceReloadListener>> typeListeners = reloadListeners.get(resourceType);
        if (typeListeners != null) {
            Set<ResourceReloadListener> set = typeListeners.get(resourceName);
            set.remove(listener);
            if (set.isEmpty()) {
                typeListeners.remove(resourceName);
            }

            if (typeListeners.isEmpty()) {
                reloadListeners.remove(resourceType);
            }
        }
    }

    /**
     * Interface for resource reload listeners
     */
    public interface ResourceReloadListener {
        void onResourceReload(Identifier resourceName, ResourceObject newResource);
    }

    /**
     * Get the reload listener for a specific resource
     * Uses double buffering - reads from the active listener buffer
     */
    private Set<ResourceReloadListener> getReloadListener(Identifier type, Identifier name) {
        Map<Identifier, Set<ResourceReloadListener>> typeListeners = reloadListeners.get(type);
        return typeListeners != null ? typeListeners.get(name) : null;
    }
}