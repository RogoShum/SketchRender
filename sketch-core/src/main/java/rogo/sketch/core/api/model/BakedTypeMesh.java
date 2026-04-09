package rogo.sketch.core.api.model;

import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.util.KeyId;

/**
 * Represents a static mesh that is resident in backend-owned geometry memory or backed by a shared source snapshot.
 * Supports zero-copy backend references when available and snapshot-driven materialization otherwise.
 *
 * <p>Zero-copy integration allows the mesh's GPU buffers to be directly referenced
 * without copying data, improving performance for static geometry.</p>
 */
public non-sealed interface BakedTypeMesh extends PreparedMesh {
    KeyId BAKED_MESH = KeyId.of("baked_mesh");

    /**
     * Get the backend geometry binding that contains this mesh's installed GPU data.
     * This allows zero-copy reference by directly reusing an already installed backend binding.
     *
     * @return The installed backend geometry binding, or null if not available
     */
    BackendGeometryBinding sourceGeometryBinding();

    SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot();
}

