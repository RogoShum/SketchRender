package rogo.sketch.core.api.model;

import rogo.sketch.core.util.KeyId;

/**
 * Represents a dynamic mesh that is generated on the CPU.
 * Supports filling data into a VertexDataBuilder.
 */
public non-sealed interface DynamicTypeMesh extends PreparedMesh {
    KeyId BASED_MESH = KeyId.of("based_mesh");
}