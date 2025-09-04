package rogo.sketch.render.vertex;

import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.filler.VertexFillerManager;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.information.RenderList;
import rogo.sketch.render.resource.buffer.VertexResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for shared VertexResource instances based on RenderSetting
 * Provides efficient caching and reuse of vertex resources with the same parameters
 */
public class VertexResourceManager {
    private static VertexResourceManager instance;
    private final Map<RenderParameter, VertexResource> resourceCache = new ConcurrentHashMap<>();

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
    public VertexResource getOrCreateVertexResource(RenderParameter parameter) {
        if (parameter.isInvalid()) {
            throw new IllegalArgumentException("RenderSetting must have a valid RenderParameter");
        }

        return resourceCache.computeIfAbsent(parameter, k -> createVertexResource(parameter));
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
    public boolean hasVertexResource(RenderParameter renderParameter) {
        if (renderParameter.isInvalid()) {
            return false;
        }

        return resourceCache.containsKey(renderParameter);
    }

    /**
     * Remove a VertexResource from cache and dispose it
     */
    public void removeVertexResource(RenderParameter renderParameter) {
        if (renderParameter.isInvalid()) {
            return;
        }

        VertexResource resource = resourceCache.remove(renderParameter);
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
     * Overloaded method to create vertex resource with specific vertex count
     */
    public VertexResource getOrCreateVertexResource(PrimitiveType primitiveType, DataFormat dataFormat, int vertexCount) {
        RenderParameter parameter = RenderParameter.create(dataFormat, primitiveType);
        VertexResource resource = getOrCreateVertexResource(parameter);

        // Ensure the resource has enough capacity for the vertex count
        if (resource.getStaticVertexCount() < vertexCount) {
            // Create a new resource with the required capacity
            // This is a simplified approach - in practice you might want to resize the existing resource
            VertexResource newResource = new VertexResource(
                    dataFormat,
                    null,
                    rogo.sketch.render.vertex.DrawMode.NORMAL,
                    primitiveType,
                    parameter.usage()
            );
            return newResource;
        }

        return resource;
    }

    /**
     * Get or create a vertex filler for the given parameters
     */
    public VertexFiller getOrCreateVertexFiller(PrimitiveType primitiveType, DataFormat dataFormat) {
        RenderParameter parameter = RenderParameter.create(dataFormat, primitiveType);
        return VertexFillerManager.getInstance().getOrCreateVertexFiller(parameter);
    }

    /**
     * Create an instanced vertex resource for the given batch
     */
    public VertexResource getOrCreateInstancedVertexResource(PrimitiveType primitiveType,
                                                             DataFormat staticFormat,
                                                             RenderList.RenderBatch batch) {
        // Get the dynamic format from the first instance's layout
        DataFormat dynamicFormat = null;
        if (!batch.getInstances().isEmpty()) {
            var firstInfo = batch.getInstances().get(0);
            if (firstInfo.hasInstancedData()) {
                dynamicFormat = firstInfo.getInstancedVertexLayout().dataFormat();
            }
        }

        // For instanced rendering, each graphics instance contributes one instance
        int totalInstanceCount = batch.getGraphicsInstanceCount();

        // Create vertex resource with both static and dynamic layouts
        return new VertexResource(
                staticFormat,           // Static vertex format (mesh geometry)
                dynamicFormat,         // Dynamic vertex format (instance data)
                DrawMode.INSTANCED,
                primitiveType,
                RenderParameter.create(staticFormat, primitiveType).usage()
        );
    }

    /**
     * Create a dynamic vertex filler for instance data
     */
    public VertexFiller getOrCreateDynamicVertexFiller(InstancedVertexLayout layout, PrimitiveType primitiveType) {
        DataFormat dynamicFormat = layout.dataFormat();

        return new VertexFiller(dynamicFormat, primitiveType);
    }
}