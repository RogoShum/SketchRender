package rogo.sketch.module.culling;

import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.backend.BackendCounterBuffer;

/**
 * Phase-A terrain mesh counter contract.
 * Keeps core free of concrete GL counter types while preserving the current
 * chunk-culling behavior and alias-buffer refresh flow.
 */
public interface TerrainMeshCounterBuffer extends BackendCounterBuffer, DataResourceObject {
    void resize(int count);

    void updateCount(int count);

    void bind();
}
