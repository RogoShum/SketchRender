package rogo.sketch.api.graphics;

import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.util.KeyId;

/**
 * Provides instance data for graphics components.
 * The format of the data is defined by the RenderParameter.
 * This interface only fills the data.
 */
public interface InstanceDataProvider { // Renamed from InstancedLayoutProvider

    /**
     * Fill instance vertex data for a specific binding point.
     *
     * @param componentKey The binding point
     * @param builder      The vertex builder to write data to
     */
    void fillInstanceData(KeyId componentKey, VertexDataBuilder builder);
}