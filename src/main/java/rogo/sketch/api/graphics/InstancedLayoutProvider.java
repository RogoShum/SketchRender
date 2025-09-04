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
     * Fill instance vertex data for this graphics instance
     * The filler will automatically advance to the next position after filling
     * @param filler The vertex filler to write data to
     */
    void fillInstanceVertexData(VertexFiller filler);
}