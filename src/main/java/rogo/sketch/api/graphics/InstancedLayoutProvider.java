package rogo.sketch.api.graphics;

import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.vertex.InstancedVertexLayout;

/**
 * Provides instanced vertex layout and data for graphics instances that support instanced rendering
 */
public interface InstancedLayoutProvider {

    /**
     * Get the vertex layout for instance data
     */
    InstancedVertexLayout getInstancedVertexLayout();

    /**
     * Fill instance vertex data for this graphics instance (backward compatibility)
     * @param filler The vertex filler to write data to
     */
    void fillInstanceVertexData(VertexFiller filler);

    /**
     * Fill instance vertex data for this graphics instance at specified index
     * @param filler The vertex filler to write data to
     * @param index The index position of this provider in the batch
     */
    void fillInstanceVertexData(VertexFiller filler, int index);
}