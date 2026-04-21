package rogo.sketch.module.culling;

import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.backend.BackendIndirectBuffer;

/**
 * Phase-A terrain indirect-command buffer contract.
 */
public interface TerrainMeshIndirectBuffer extends BackendIndirectBuffer, DataResourceObject {
    void resize(long commandCapacity);
}
