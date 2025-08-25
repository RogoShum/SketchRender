package rogo.sketch.render.vertex;

import com.mojang.blaze3d.platform.GlDebug;
import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.RenderSetting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manager for shared VertexResource instances based on RenderSetting
 * Provides efficient caching and reuse of vertex resources with the same parameters
 */
public class VertexResourceManager {
    private static VertexResourceManager instance;
    
    // Cache: RenderParameterKey -> VertexResource
    private final Map<RenderParameterKey, VertexResource> resourceCache = new ConcurrentHashMap<>();
    
    private VertexResourceManager() {
    }
    
    public static VertexResourceManager getInstance() {
        if (instance == null) {
            instance = new VertexResourceManager();
        }
        return instance;
    }
    
    /**
     * Get or create a VertexResource for the given RenderSetting
     * Returns a shared instance if one with the same parameters already exists
     */
    public VertexResource getOrCreateVertexResource(RenderSetting setting) {
        if (setting.renderParameter() == null || setting.renderParameter() == RenderParameter.EMPTY) {
            throw new IllegalArgumentException("RenderSetting must have a valid RenderParameter");
        }
        
        RenderParameterKey key = new RenderParameterKey(setting.renderParameter());

        return resourceCache.computeIfAbsent(key, k -> createVertexResource(setting.renderParameter()));
    }
    
    /**
     * Create a new VertexResource based on RenderParameter
     */
    private VertexResource createVertexResource(RenderParameter parameter) {
        return new VertexResource(
            parameter.dataFormat(),
            null, // No dynamic format for shared resources
            DrawMode.NORMAL,
            parameter.primitiveType(),
            parameter.usage()
        );
    }
    
    /**
     * Check if a VertexResource exists for the given RenderSetting
     */
    public boolean hasVertexResource(RenderSetting setting) {
        if (setting.renderParameter() == null || setting.renderParameter() == RenderParameter.EMPTY) {
            return false;
        }
        
        RenderParameterKey key = new RenderParameterKey(setting.renderParameter());
        return resourceCache.containsKey(key);
    }
    
    /**
     * Remove a VertexResource from cache and dispose it
     */
    public void removeVertexResource(RenderSetting setting) {
        if (setting.renderParameter() == null || setting.renderParameter() == RenderParameter.EMPTY) {
            return;
        }
        
        RenderParameterKey key = new RenderParameterKey(setting.renderParameter());
        VertexResource resource = resourceCache.remove(key);
        if (resource != null) {
            resource.dispose();
        }
    }
    
    /**
     * Clear all cached VertexResources
     */
    public void clearAll() {
        resourceCache.values().forEach(VertexResource::dispose);
        resourceCache.clear();
    }
    
    /**
     * Get cache statistics for debugging
     */
    public String getCacheStats() {
        return String.format("VertexResourceManager: %d cached resources", resourceCache.size());
    }
    
    /**
     * Key class for caching VertexResources based on RenderParameter properties
     */
    private static class RenderParameterKey {
        private final RenderParameter parameter;
        private final int hashCode;
        
        public RenderParameterKey(RenderParameter parameter) {
            this.parameter = parameter;
            // Pre-compute hash code for performance
            this.hashCode = Objects.hash(
                parameter.dataFormat(),
                parameter.primitiveType(),
                parameter.usage(),
                parameter.enableIndexBuffer(),
                parameter.enableSorting()
            );
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            
            RenderParameterKey that = (RenderParameterKey) obj;
            return Objects.equals(parameter.dataFormat(), that.parameter.dataFormat()) &&
                   parameter.primitiveType() == that.parameter.primitiveType() &&
                   parameter.usage() == that.parameter.usage() &&
                   parameter.enableIndexBuffer() == that.parameter.enableIndexBuffer() &&
                   parameter.enableSorting() == that.parameter.enableSorting();
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
        
        @Override
        public String toString() {
            return "RenderParameterKey{" +
                    "format=" + parameter.dataFormat() +
                    ", primitiveType=" + parameter.primitiveType() +
                    ", usage=" + parameter.usage() +
                    ", indexBuffer=" + parameter.enableIndexBuffer() +
                    ", sorting=" + parameter.enableSorting() +
                    '}';
        }
    }
}
