package rogo.sketch.api.model;

import org.joml.Matrix4f;
import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.util.KeyId;

/**
 * Represents a dynamic mesh that is generated on the CPU.
 * Supports filling data into a VertexDataBuilder.
 */
public non-sealed interface DynamicTypeMesh extends PreparedMesh {
    KeyId BASED_MESH = KeyId.of("based_mesh");

    /**
     * Fills the provided VertexDataBuilder with this mesh's vertex and index data.
     *
     * @param componentKey The binding point
     * @param builder   The vertex data builder to write data into.
     * @param transform Optional transform to apply to vertex positions (can be null).
     */
    void fill(KeyId componentKey, VertexDataBuilder builder, Matrix4f transform);
}