package rogo.sketch.render.vertex;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import rogo.sketch.render.data.filler.VertexFiller;

/**
 * External renderer that uses VertexResource for rendering operations.
 * This class handles the actual drawing commands based on the vertex resource configuration.
 */
public class VertexRenderer {

    /**
     * Render a vertex resource with the specified primitive mode
     *
     * @param resource      The vertex resource to render
     */
    public static void render(VertexResource resource) {
        if (resource.getStaticVertexCount() == 0) {
            return; // Nothing to render
        }

        // Bind VAO and index buffer
        resource.bind();

        try {
            if (resource.hasIndices()) {
                // Render with index buffer
                IndexBufferResource indexBuffer = resource.getIndexBuffer();
                int indexCount = indexBuffer.getIndexCount();

                if (resource.hasInstances()) {
                    // Instanced indexed rendering
                    GL31.glDrawElementsInstanced(
                            resource.getPrimitiveType(),
                            indexCount,
                            GL15.GL_UNSIGNED_INT,
                            0,
                            resource.getInstanceCount()
                    );
                } else {
                    // Regular indexed rendering
                    GL15.glDrawElements(
                            resource.getPrimitiveType(),
                            indexCount,
                            GL15.GL_UNSIGNED_INT,
                            0
                    );
                }
            } else {
                // Render without index buffer
                if (resource.hasInstances()) {
                    // Instanced array rendering
                    GL31.glDrawArraysInstanced(
                            resource.getPrimitiveType(),
                            0,
                            resource.getStaticVertexCount(),
                            resource.getInstanceCount()
                    );
                } else {
                    // Regular array rendering
                    GL15.glDrawArrays(
                            resource.getPrimitiveType(),
                            0,
                            resource.getStaticVertexCount()
                    );
                }
            }
        } finally {
            // Always unbind after rendering
            resource.unbind();
        }
    }

    /**
     * Render with custom parameters
     *
     * @param resource      The vertex resource to render
     * @param first         First vertex/index to render
     * @param count         Number of vertices/indices to render
     */
    public static void render(VertexResource resource, int first, int count) {
        resource.bind();

        try {
            if (resource.hasIndices()) {
                GL15.glDrawElements(resource.getPrimitiveType(), count, GL15.GL_UNSIGNED_INT, first * 4L); // 4 bytes per int
            } else {
                GL15.glDrawArrays(resource.getPrimitiveType(), first, count);
            }
        } finally {
            resource.unbind();
        }
    }

    /**
     * Render with instancing and custom parameters
     *
     * @param resource      The vertex resource to render
     * @param primitiveMode OpenGL primitive mode
     * @param first         First vertex/index to render
     * @param count         Number of vertices/indices to render
     * @param instanceCount Number of instances to render
     */
    public static void renderInstanced(VertexResource resource, int primitiveMode,
                                       int first, int count, int instanceCount) {
        resource.bind();

        try {
            if (resource.hasIndices()) {
                GL31.glDrawElementsInstanced(primitiveMode, count, GL15.GL_UNSIGNED_INT, first * 4, instanceCount);
            } else {
                GL31.glDrawArraysInstanced(primitiveMode, first, count, instanceCount);
            }
        } finally {
            resource.unbind();
        }
    }

    /**
     * Render using the recommended draw mode from the resource
     *
     * @param resource The vertex resource to render
     */
    public static void renderAuto(VertexResource resource) {
        DrawMode drawMode = resource.getDrawMode();

        switch (drawMode) {
            case NORMAL -> render(resource);
            case INSTANCED -> {
                if (resource.hasInstances()) {
                    render(resource);
                } else {
                    // Fall back to normal rendering if no instances
                    render(resource);
                }
            }
        }
    }

    /**
     * Get the appropriate index type for the given vertex count
     * This is a utility method for external use
     */
    public static VertexFiller.IndexType getOptimalIndexType(int maxVertexIndex) {
        return VertexFiller.IndexType.getOptimalType(maxVertexIndex);
    }

    /**
     * Get render statistics for debugging
     */
    public static RenderStats getRenderStats(VertexResource resource) {
        return new RenderStats(
                resource.getStaticVertexCount(),
                resource.getInstanceCount(),
                resource.hasIndices() ? resource.getIndexBuffer().getIndexCount() : 0,
                resource.hasIndices(),
                resource.hasInstances(),
                resource.getDrawMode()
        );
    }

    /**
     * Render statistics for debugging and profiling
     */
    public static class RenderStats {
        public final int vertexCount;
        public final int instanceCount;
        public final int indexCount;
        public final boolean hasIndices;
        public final boolean hasInstances;
        public final DrawMode drawMode;

        public RenderStats(int vertexCount, int instanceCount, int indexCount,
                           boolean hasIndices, boolean hasInstances, DrawMode drawMode) {
            this.vertexCount = vertexCount;
            this.instanceCount = instanceCount;
            this.indexCount = indexCount;
            this.hasIndices = hasIndices;
            this.hasInstances = hasInstances;
            this.drawMode = drawMode;
        }

        public int getDrawCallCount() {
            return hasInstances && instanceCount > 0 ? instanceCount : 1;
        }

        public int getTriangleCount(int primitiveMode) {
            int primitiveCount = hasIndices ? indexCount : vertexCount;

            return switch (primitiveMode) {
                case GL15.GL_TRIANGLES -> primitiveCount / 3;
                case GL15.GL_TRIANGLE_STRIP, GL15.GL_TRIANGLE_FAN -> Math.max(0, primitiveCount - 2);
                default -> 0; // Lines, points, etc.
            };
        }

        @Override
        public String toString() {
            return String.format(
                    "RenderStats{vertices=%d, instances=%d, indices=%d, hasIndices=%s, hasInstances=%s, drawMode=%s}",
                    vertexCount, instanceCount, indexCount, hasIndices, hasInstances, drawMode
            );
        }
    }
} 