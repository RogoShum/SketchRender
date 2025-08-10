package rogo.sketch.render.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.loader.*;
import rogo.sketch.util.Identifier;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central graphics resource management system.
 * Handles registration, lookup, and reloading of all graphics resources.
 */
public class GraphicsResourceManager {
    
    private static GraphicsResourceManager instance;
    
    // Resource storage: Type -> (Name -> Resource)
    private final Map<Identifier, Map<Identifier, ResourceObject>> resources = new ConcurrentHashMap<>();
    
    // Manual (code-controlled) resources: Type -> (Name -> Supplier)
    private final Map<Identifier, Map<Identifier, Supplier<ResourceObject>>> manualResources = new ConcurrentHashMap<>();
    
    // JSON-defined resources (reloadable): Type -> (Name -> JSON data)
    private final Map<Identifier, Map<Identifier, String>> jsonResources = new ConcurrentHashMap<>();
    
    // Resource references for external use
    private final Map<String, ResourceReference<?>> references = new ConcurrentHashMap<>();
    
    // Resource loaders for different types
    private final ResourceLoaderRegistry loaderRegistry = new ResourceLoaderRegistry();
    
    // JSON processor
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new Identifier.GsonAdapter())
            .setPrettyPrinting()
            .create();
    
    private GraphicsResourceManager() {
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
    public <T extends ResourceObject> void registerManual(Identifier type, Identifier name, Supplier<T> resourceSupplier) {
        manualResources.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                      .put(name, () -> resourceSupplier.get());
        
        // Invalidate existing references
        invalidateReferences(type, name);
    }
    
    /**
     * Register a JSON-defined resource
     */
    public void registerJson(Identifier type, Identifier name, String jsonData) {
        jsonResources.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                    .put(name, jsonData);
        
        // Load the resource immediately
        loadJsonResource(type, name, jsonData);
        
        // Invalidate existing references
        invalidateReferences(type, name);
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
        return (ResourceReference<T>) references.computeIfAbsent(key, k -> 
            new ResourceReference<>(name, type, () -> getResource(type, name))
        );
    }
    
    /**
     * Get a resource directly (may return null)
     */
    @SuppressWarnings("unchecked")
    public <T extends ResourceObject> Optional<T> getResource(Identifier type, Identifier name) {
        // Try direct resources first
        Map<Identifier, ResourceObject> typeResources = resources.get(type);
        if (typeResources != null) {
            ResourceObject resource = typeResources.get(name);
            if (resource != null) {
                return Optional.of((T) resource);
            }
        }
        
        // Try manual resources
        Map<Identifier, Supplier<ResourceObject>> typeManual = manualResources.get(type);
        if (typeManual != null) {
            Supplier<ResourceObject> supplier = typeManual.get(name);
            if (supplier != null) {
                try {
                    ResourceObject resource = supplier.get();
                    if (resource != null) {
                        return Optional.of((T) resource);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to create manual resource " + name + " of type " + type + ": " + e.getMessage());
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Reload all JSON-defined resources (for resource pack switching)
     */
    public void reloadJsonResources() {
        // Dispose existing JSON resources
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
        
        // Reload all JSON resources
        for (Identifier type : jsonResources.keySet()) {
            Map<Identifier, String> typeJsons = jsonResources.get(type);
            for (Map.Entry<Identifier, String> entry : typeJsons.entrySet()) {
                loadJsonResource(type, entry.getKey(), entry.getValue());
            }
        }
        
        // Invalidate all references to force refresh
        for (ResourceReference<?> ref : references.values()) {
            ref.invalidate();
        }
    }
    
    /**
     * Load a JSON resource
     */
    private void loadJsonResource(Identifier type, Identifier name, String jsonData) {
        ResourceLoader<?> loader = loaderRegistry.getLoader(type);
        if (loader == null) {
            System.err.println("No loader found for resource type: " + type);
            return;
        }
        
        try {
            ResourceObject resource = loader.loadFromJson(jsonData, gson);
            if (resource != null) {
                resources.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                         .put(name, resource);
            }
        } catch (Exception e) {
            System.err.println("Failed to load JSON resource " + name + " of type " + type + ": " + e.getMessage());
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
     * Get all resources of a specific type
     */
    public <T extends ResourceObject> Map<Identifier, T> getResourcesOfType(Identifier type) {
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
        Map<Identifier, Supplier<ResourceObject>> typeManual = manualResources.get(type);
        if (typeManual != null) {
            for (Map.Entry<Identifier, Supplier<ResourceObject>> entry : typeManual.entrySet()) {
                try {
                    @SuppressWarnings("unchecked")
                    T resource = (T) entry.getValue().get();
                    if (resource != null) {
                        result.put(entry.getKey(), resource);
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
        Map<Identifier, Supplier<ResourceObject>> typeManual = manualResources.get(type);
        if (typeManual != null) {
            typeManual.remove(name);
        }
        
        // Remove from JSON resources
        Map<Identifier, String> typeJson = jsonResources.get(type);
        if (typeJson != null) {
            typeJson.remove(name);
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
        manualResources.clear();
        jsonResources.clear();
        references.clear();
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
} 