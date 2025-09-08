package rogo.sketch.api.graphics;

/**
 * Provides vertex count information for graphics instances that don't use mesh data
 */
public interface VertexCountProvider extends GraphicsInstance {
    
    /**
     * Get the number of vertices this graphics instance will render
     * @return The vertex count
     */
    int getVertexCount();
}