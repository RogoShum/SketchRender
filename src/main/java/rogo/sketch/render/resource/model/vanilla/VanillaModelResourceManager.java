package rogo.sketch.render.resource.model.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resource manager for integrating Minecraft models with the graphics resource system.
 * Provides automatic registration and lazy loading of Minecraft models.
 */
public class VanillaModelResourceManager {
    
    private static VanillaModelResourceManager instance;
    private final Map<String, ResourceLocation> registeredModels = new ConcurrentHashMap<>();
    private final GraphicsResourceManager resourceManager;
    private final MinecraftModelConverter converter;
    
    private VanillaModelResourceManager() {
        this.resourceManager = GraphicsResourceManager.getInstance();
        this.converter = MinecraftModelConverter.getInstance();
    }
    
    public static VanillaModelResourceManager getInstance() {
        if (instance == null) {
            instance = new VanillaModelResourceManager();
        }
        return instance;
    }
    
    /**
     * Register a Minecraft model to be available as a mesh resource
     */
    public void registerModel(String resourceName, ResourceLocation minecraftLocation) {
        registeredModels.put(resourceName, minecraftLocation);
        
        // Register as a mutable resource (lazy loading)
        Identifier resourceId = Identifier.of(resourceName);
        resourceManager.registerMutable(
            ResourceTypes.MESH,
            resourceId,
            new MinecraftModelConverter.MinecraftModelSupplier(minecraftLocation)
        );
    }
    
    /**
     * Register multiple models at once
     */
    public void registerModels(Map<String, ResourceLocation> models) {
        for (Map.Entry<String, ResourceLocation> entry : models.entrySet()) {
            registerModel(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Register common Minecraft block models
     */
    public void registerCommonBlockModels() {
        Map<String, ResourceLocation> commonModels = new HashMap<>();
        
        // Basic block models
        commonModels.put("minecraft:cube", new ResourceLocation("minecraft", "block/cube"));
        commonModels.put("minecraft:cube_all", new ResourceLocation("minecraft", "block/cube_all"));
        commonModels.put("minecraft:cube_top", new ResourceLocation("minecraft", "block/cube_top"));
        commonModels.put("minecraft:cube_bottom_top", new ResourceLocation("minecraft", "block/cube_bottom_top"));
        
        // Slab models
        commonModels.put("minecraft:slab", new ResourceLocation("minecraft", "block/slab"));
        commonModels.put("minecraft:slab_top", new ResourceLocation("minecraft", "block/slab_top"));
        
        // Stairs models
        commonModels.put("minecraft:stairs", new ResourceLocation("minecraft", "block/stairs"));
        commonModels.put("minecraft:inner_stairs", new ResourceLocation("minecraft", "block/inner_stairs"));
        commonModels.put("minecraft:outer_stairs", new ResourceLocation("minecraft", "block/outer_stairs"));
        
        // Cross models (for plants, etc.)
        commonModels.put("minecraft:cross", new ResourceLocation("minecraft", "block/cross"));
        commonModels.put("minecraft:tinted_cross", new ResourceLocation("minecraft", "block/tinted_cross"));
        
        // Fence models
        commonModels.put("minecraft:fence_post", new ResourceLocation("minecraft", "block/fence_post"));
        commonModels.put("minecraft:fence_side", new ResourceLocation("minecraft", "block/fence_side"));
        
        registerModels(commonModels);
    }
    
    /**
     * Register common item models
     */
    public void registerCommonItemModels() {
        Map<String, ResourceLocation> itemModels = new HashMap<>();
        
        // Basic item models
        itemModels.put("minecraft:item_generated", new ResourceLocation("minecraft", "item/generated"));
        itemModels.put("minecraft:item_handheld", new ResourceLocation("minecraft", "item/handheld"));
        itemModels.put("minecraft:item_handheld_rod", new ResourceLocation("minecraft", "item/handheld_rod"));
        
        // Tool models
        itemModels.put("minecraft:sword", new ResourceLocation("minecraft", "item/handheld"));
        itemModels.put("minecraft:pickaxe", new ResourceLocation("minecraft", "item/handheld"));
        itemModels.put("minecraft:axe", new ResourceLocation("minecraft", "item/handheld"));
        itemModels.put("minecraft:shovel", new ResourceLocation("minecraft", "item/handheld"));
        itemModels.put("minecraft:hoe", new ResourceLocation("minecraft", "item/handheld"));
        
        registerModels(itemModels);
    }
    
    /**
     * Register models for a specific mod
     */
    public void registerModModels(String modId, Map<String, String> modelPaths) {
        Map<String, ResourceLocation> modModels = new HashMap<>();
        
        for (Map.Entry<String, String> entry : modelPaths.entrySet()) {
            String resourceName = modId + ":" + entry.getKey();
            ResourceLocation mcLocation = new ResourceLocation(modId, entry.getValue());
            modModels.put(resourceName, mcLocation);
        }
        
        registerModels(modModels);
    }
    
    /**
     * Auto-register all available block models from current resource pack
     */
    public void autoRegisterBlockModels() {
        try {
            // This would need to be implemented to scan available models
            // For now, just register the common ones
            registerCommonBlockModels();
            
            System.out.println("Auto-registered common block models");
            
        } catch (Exception e) {
            System.err.println("Failed to auto-register block models: " + e.getMessage());
        }
    }
    
    /**
     * Get statistics about registered models
     */
    public RegistrationStats getStats() {
        int totalRegistered = registeredModels.size();
        int totalCached = converter.getCacheStats().totalModels;
        
        return new RegistrationStats(totalRegistered, totalCached);
    }
    
    /**
     * Clear all registrations and cache
     */
    public void clear() {
        registeredModels.clear();
        converter.clearCache();
    }
    
    /**
     * Get all registered model names
     */
    public Set<String> getRegisteredModelNames() {
        return registeredModels.keySet();
    }
    
    /**
     * Check if a model is registered
     */
    public boolean isModelRegistered(String resourceName) {
        return registeredModels.containsKey(resourceName);
    }
    
    /**
     * Get the Minecraft location for a registered model
     */
    public ResourceLocation getMinecraftLocation(String resourceName) {
        return registeredModels.get(resourceName);
    }
    
    /**
     * Force load and cache a specific model
     */
    public void preloadModel(String resourceName) {
        ResourceLocation mcLocation = registeredModels.get(resourceName);
        if (mcLocation != null) {
            converter.getOrCreateModelMesh(mcLocation);
        }
    }
    
    /**
     * Preload multiple models
     */
    public void preloadModels(Set<String> resourceNames) {
        for (String name : resourceNames) {
            preloadModel(name);
        }
    }
    
    /**
     * Registration statistics
     */
    public static class RegistrationStats {
        public final int totalRegistered;
        public final int totalCached;
        
        RegistrationStats(int totalRegistered, int totalCached) {
            this.totalRegistered = totalRegistered;
            this.totalCached = totalCached;
        }
        
        @Override
        public String toString() {
            return "RegistrationStats{" +
                    "registered=" + totalRegistered +
                    ", cached=" + totalCached +
                    '}';
        }
    }
}
