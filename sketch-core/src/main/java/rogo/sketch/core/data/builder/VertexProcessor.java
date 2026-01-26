package rogo.sketch.core.data.builder;

/**
 * Interface for components that process vertex data during the building phase.
 * Allows separating concerns like sorting, indexing, and command generation.
 */
public interface VertexProcessor {
    
    /**
     * Called when the builder starts.
     */
    default void onStartBuild(VertexDataBuilder builder) {}

    /**
     * Called before a vertex is started.
     */
    default void onStartVertex(VertexDataBuilder builder, int vertexIndex) {}

    /**
     * Called after a vertex is ended.
     */
    default void onEndVertex(VertexDataBuilder builder, int vertexIndex) {}

    /**
     * Called when the builder finishes (flushes).
     * Can be used to perform post-processing like sorting or index generation.
     */
    default void onFinish(VertexDataBuilder builder) {}
}

