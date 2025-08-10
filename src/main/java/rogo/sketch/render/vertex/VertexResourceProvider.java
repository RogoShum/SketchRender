package rogo.sketch.render.vertex;

import rogo.sketch.render.data.filler.VertexFiller;

/**
 * Interface for objects that can provide their own VertexResource
 * Used by GraphInstances that need independent vertex resources
 */
public interface VertexResourceProvider {
    
    /**
     * Get the vertex resource type for this provider
     */
    VertexResourceType getVertexResourceType();
    
    /**
     * Get or create the VertexResource for this provider
     * Called by GraphPassGroup when needed
     */
    VertexResource getOrCreateVertexResource();
    
    /**
     * Check if the vertex resource needs updating
     * For static resources, this should return false after first upload
     */
    boolean needsVertexUpdate();
    
    /**
     * Fill vertex data into the provided filler
     * Only called if needsVertexUpdate() returns true or for shared resources
     */
    void fillVertexData(VertexFiller filler);
    
    /**
     * Handle custom rendering if needed
     * Called instead of standard batch rendering for instance resources
     */
    default void customRender() {
        VertexResource resource = getOrCreateVertexResource();
        if (resource != null) {
            VertexRenderer.render(resource);
        }
    }
}
