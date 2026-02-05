package rogo.sketch.vanilla.resource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.PackFeatureDefinition;
import rogo.sketch.core.resource.ResourceScanProvider;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Minecraft-specific resource manager that implements ResourceScanProvider.
 * Bridges the Minecraft resource system with the sketch-core resource management.
 */
public class RenderResourceManager implements ResourceManagerReloadListener, ResourceScanProvider {
    
    private ResourceManager resourceManager;
    
    // Resource type to path prefix mapping
    private static final Map<KeyId, String> TYPE_TO_PATH = new HashMap<>();
    
    static {
        TYPE_TO_PATH.put(ResourceTypes.MACRO_TEMPLATE, "render/resource/macro_template");
        TYPE_TO_PATH.put(ResourceTypes.SHADER_TEMPLATE, "render/resource/shader_template");
        TYPE_TO_PATH.put(ResourceTypes.TEXTURE, "render/resource/texture");
        TYPE_TO_PATH.put(ResourceTypes.RENDER_TARGET, "render/resource/render_target");
        TYPE_TO_PATH.put(ResourceTypes.PARTIAL_RENDER_SETTING, "render/resource/partial_render_setting");
        TYPE_TO_PATH.put(ResourceTypes.MESH, "render/resource/mesh");
        TYPE_TO_PATH.put(ResourceTypes.FUNCTION, "render/resource/function");
        TYPE_TO_PATH.put(ResourceTypes.DRAW_CALL, "render/resource/draw_call");
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        
        // Set this as the scan provider and trigger reload
        GraphicsResourceManager.getInstance().setScanProvider(this);
        GraphicsResourceManager.getInstance().reload();
    }
    
    // ===== ResourceScanProvider Implementation =====
    
    @Override
    public Map<KeyId, InputStream> scanResources(KeyId resourceType) {
        if (resourceManager == null) {
            return Collections.emptyMap();
        }
        
        String pathPrefix = TYPE_TO_PATH.get(resourceType);
        if (pathPrefix == null) {
            return Collections.emptyMap();
        }
        
        Map<KeyId, InputStream> result = new HashMap<>();
        
        Map<ResourceLocation, Resource> found = resourceManager.listResources(
            pathPrefix,
            loc -> loc.getPath().endsWith(".json")
        );
        
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                String identifier = getIdentifierWithoutExtension(id);
                InputStream stream = entry.getValue().open();
                result.put(KeyId.of(identifier), stream);
            } catch (IOException e) {
                System.err.println("Failed to open resource " + id + ": " + e.getMessage());
            }
        }
        
        return result;
    }
    
    @Override
    public Optional<InputStream> getSubResource(KeyId identifier) {
        if (resourceManager == null) {
            return Optional.empty();
        }
        
        try {
            ResourceLocation loc = new ResourceLocation(identifier.toString());
            InputStream inputStream = resourceManager.open(loc);
            return Optional.of(inputStream);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    @Override
    public List<PackFeatureDefinition> getPackFeatures() {
        if (resourceManager == null) {
            return Collections.emptyList();
        }
        
        List<PackFeatureDefinition> features = new ArrayList<>();
        
        // Scan for pack feature definitions
        String featurePath = "render/pack_features";
        Map<ResourceLocation, Resource> found = resourceManager.listResources(
            featurePath,
            loc -> loc.getPath().endsWith(".json")
        );
        
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            try {
                PackFeatureDefinition feature = parsePackFeature(entry.getKey(), entry.getValue());
                if (feature != null && !feature.isEmpty()) {
                    features.add(feature);
                }
            } catch (Exception e) {
                System.err.println("Failed to load pack feature " + entry.getKey() + ": " + e.getMessage());
            }
        }
        
        return features;
    }
    
    /**
     * Parse a pack feature definition from a resource.
     */
    private PackFeatureDefinition parsePackFeature(ResourceLocation id, Resource resource) {
        try (var reader = new BufferedReader(new InputStreamReader(resource.open()))) {
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            
            String packId = id.getNamespace() + ":" + getIdentifierWithoutExtension(id);
            
            Set<String> featureFlags = new HashSet<>();
            if (json.has("features") && json.get("features").isJsonArray()) {
                for (var element : json.getAsJsonArray("features")) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        featureFlags.add(element.getAsString());
                    }
                }
            }
            
            Map<String, String> macros = new HashMap<>();
            if (json.has("macros") && json.get("macros").isJsonObject()) {
                for (var entry : json.getAsJsonObject("macros").entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        macros.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            
            return new PackFeatureDefinition(packId, featureFlags, macros);
            
        } catch (IOException e) {
            System.err.println("Failed to parse pack feature: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract identifier without file extension from a ResourceLocation.
     */
    public static String getIdentifierWithoutExtension(ResourceLocation loc) {
        String path = loc.getPath();

        int lastSlash = path.lastIndexOf('/');
        String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot);
        }
        return loc.getNamespace() + ":" + fileName;
    }
}