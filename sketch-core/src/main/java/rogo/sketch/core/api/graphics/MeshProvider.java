package rogo.sketch.core.api.graphics;

import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexDataBuilder;
import rogo.sketch.core.util.KeyId;

public interface MeshProvider {
    PreparedMesh getPreparedMesh();

    /**
     * Fill vertex data for a specific binding point.
     *
     * @param componentKey The binding point
     * @param builder      The vertex builder to write data to
     */
    void fillVertex(KeyId componentKey, VertexDataBuilder builder);
}