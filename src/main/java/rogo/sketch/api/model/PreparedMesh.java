package rogo.sketch.api.model;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;

/**
 * The base interface for all mesh types in the rendering pipeline.
 * Represents a collection of vertices and indices ready for rendering.
 */
public sealed interface PreparedMesh permits BakedTypeMesh, DynamicTypeMesh {

    /**
     * @return The primitive type (TRIANGLES, LINES, etc.)
     */
    PrimitiveType getPrimitiveType();

    /**
     * @return The data format of the vertices stored in this mesh.
     */
    DataFormat getVertexFormat();

    /**
     * @return The number of vertices in this mesh.
     */
    int getVertexCount();

    /**
     * @return The number of indices in this mesh. Returns 0 if not indexed.
     */
    int getIndicesCount();

    /**
     * Checks if this mesh has an index buffer.
     */
    default boolean isIndexed() {
        return getIndicesCount() > 0;
    }
}