package rogo.sketch.api.model;

import org.joml.Matrix4f;
import rogo.sketch.render.data.builder.VertexDataBuilder;

/**
 * Represents a dynamic mesh that is generated on the CPU.
 * Supports filling data into a VertexDataBuilder.
 */
public non-sealed interface DynamicTypeMesh extends PreparedMesh {
    /**
     * Fills the provided VertexDataBuilder with this mesh's vertex and index data.
     *
     * @param bindingPoint The binding point
     * @param builder   The vertex data builder to write data into.
     * @param transform Optional transform to apply to vertex positions (can be null).
     */
    void fill(int bindingPoint, VertexDataBuilder builder, Matrix4f transform);
}