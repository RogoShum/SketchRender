package rogo.sketch.core.backend;

import rogo.sketch.core.data.IndexType;
import rogo.sketch.core.util.KeyId;

/**
 * Backend-owned geometry binding that supports dynamic upload/update operations.
 */
public interface BackendMutableGeometryBinding extends BackendGeometryBinding, BackendGeometryMetadata {
    void uploadVertexComponent(KeyId componentId, byte[] data);

    void uploadIndices(int[] indices);

    boolean hasIndices();

    IndexType indexType();
}

