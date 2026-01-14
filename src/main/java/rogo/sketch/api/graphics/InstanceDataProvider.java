package rogo.sketch.api.graphics;

import rogo.sketch.render.data.builder.VertexDataBuilder;

/**
 * Provides instance data for graphics components.
 * The format of the data is defined by the RenderParameter.
 * This interface only fills the data.
 */
public interface InstanceDataProvider { // Renamed from InstancedLayoutProvider

    /**
     * Fill instance vertex data for a specific binding point.
     *
     * @param bindingPoint The binding point
     * @param builder      The vertex builder to write data to
     */
    void fillInstanceData(int bindingPoint, VertexDataBuilder builder);
}