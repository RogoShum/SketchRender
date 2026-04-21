package rogo.sketch.core.vertex;

import rogo.sketch.core.data.format.VertexBufferKey;

import java.util.Objects;

/**
 * Stable residency identity for a backend geometry binding.
 */
public record MeshResidencyKey(VertexBufferKey vertexBufferKey) {
    public MeshResidencyKey {
        Objects.requireNonNull(vertexBufferKey, "vertexBufferKey");
    }

    public static MeshResidencyKey from(VertexBufferKey vertexBufferKey) {
        return new MeshResidencyKey(vertexBufferKey);
    }
}
