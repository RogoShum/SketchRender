package rogo.sketch.api.model;

import rogo.sketch.render.resource.buffer.VertexResource;

/**
 * Represents a static mesh that is resident in GPU memory or backed by a resource.
 * Supports both zero-copy reference and efficient copying to a target VertexResource.
 *
 * <p>Zero-copy integration allows the mesh's GPU buffers to be directly referenced
 * without copying data, improving performance for static geometry.</p>
 */
public non-sealed interface BakedTypeMesh extends PreparedMesh {

    /**
     * Get the source VertexResource that contains this mesh's GPU data.
     * This allows zero-copy reference by directly using the existing VAO/VBO.
     *
     * @return The source VertexResource, or null if not available
     */
    VertexResource getSourceResource();

    int getVAOHandle();

    int getSourceVertexOffset();

    int getSourceIndexOffset();
}