package rogo.sketch.render.vertex;

/**
 * Vertex resource usage types for different rendering scenarios
 */
public enum VertexResourceType {
    /**
     * Shared dynamic resource for batching multiple instances
     * Managed by GraphPassGroup, filled every frame
     */
    SHARED_DYNAMIC,
    
    /**
     * Static resource for single object, filled once and reused
     * Managed by GraphInstance itself
     */
    INSTANCE_STATIC,
    
    /**
     * Dynamic resource for single instanced object
     * Managed by GraphInstance, updated as needed
     */
    INSTANCE_DYNAMIC,
    
    /**
     * Instanced resource for multiple copies of same geometry
     * Managed by GraphInstance, static geometry + dynamic instance data
     */
    INSTANCE_INSTANCED
}
