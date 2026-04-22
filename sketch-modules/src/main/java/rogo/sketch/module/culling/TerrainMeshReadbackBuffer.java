package rogo.sketch.module.culling;

import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.backend.BackendReadbackBuffer;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledBuffer;

/**
 * Phase-A terrain readback buffer contract used by indirect terrain rendering.
 */
public interface TerrainMeshReadbackBuffer
        extends BackendReadbackBuffer, BackendInstalledBuffer, BackendInstalledBindableResource, DataResourceObject {
}
