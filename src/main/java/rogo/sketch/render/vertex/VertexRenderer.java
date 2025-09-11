package rogo.sketch.render.vertex;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL45;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.resource.buffer.IndexBufferResource;
import rogo.sketch.render.resource.buffer.VertexResource;

/**
 * External renderer that uses VertexResource for rendering operations.
 * This class handles the actual drawing commands based on the vertex resource configuration.
 * 
 * Unified rendering approach using glDrawElementsInstancedBaseVertexBaseInstance for all cases.
 */
public class VertexRenderer {

    /**
     * Unified rendering method using glDrawElementsInstancedBaseVertexBaseInstance
     * This is the preferred method that supports all offset types
     */
    public static void renderWithOffsets(VertexResource resource,
                                        PrimitiveType primitiveType,
                                        int indexCount,
                                        long indexOffset,
                                        int baseVertex,
                                        int instanceCount,
                                        int baseInstance) {
        if (indexCount == 0 || instanceCount == 0) {
            return;
        }

        resource.bind();
        try {
            // Ensure index buffer is uploaded
            if (resource.hasIndices()) {
                IndexBufferResource indexBuffer = resource.getIndexBuffer();
                if (indexBuffer.isDirty()) {
                    indexBuffer.upload();
                }
            }

            // Use the unified GL call with all offsets
            GL45.glDrawElements(
                    primitiveType.getGLType(),
                    indexCount,
                    resource.getIndexBuffer().currentIndexType().glType, // Assuming 32-bit indices
                    baseVertex
            );
        } finally {
            resource.unbind();
        }
    }

    /**
     * Render elements with base vertex support (single instance)
     * Wrapper around the unified call for single-instance rendering
     */
    public static void renderElements(VertexResource resource,
                                     PrimitiveType primitiveType,
                                     int indexCount,
                                     long indexOffset,
                                     int baseVertex) {
        renderWithOffsets(resource, primitiveType, indexCount, indexOffset, baseVertex, 1, 0);
    }

    /**
     * Render an entire vertex resource (convenience method)
     * Uses the unified call with calculated parameters from the resource
     *
     * @param resource The vertex resource to render
     */
    public static void render(VertexResource resource) {
        if (resource.getStaticVertexCount() == 0) {
            return; // Nothing to render
        }

        // Calculate parameters for the entire resource
        int indexCount;
        if (resource.hasIndices()) {
            indexCount = resource.getIndexBuffer().getIndexCount();
        } else {
            // For non-indexed rendering, we need to use arrays instead
            renderArrays(resource);
            return;
        }

        int instanceCount = resource.hasInstances() ? resource.getDynamicVertexCount() : 1;

        // Use the unified call for the entire resource
        renderWithOffsets(
                resource,
                resource.getPrimitiveType(),
                indexCount,
                0,      // indexOffset = 0 (start from beginning)
                0,      // baseVertex = 0 (start from beginning)
                instanceCount,
                0       // baseInstance = 0 (start from beginning)
        );
    }

    /**
     * Render arrays when no index buffer is available
     * This is the fallback for non-indexed rendering
     */
    private static void renderArrays(VertexResource resource) {
        resource.bind();
        try {
            if (resource.hasInstances()) {
                // Instanced array rendering
                GL31.glDrawArraysInstanced(
                        resource.getPrimitiveType().getGLType(),
                        0,
                        resource.getStaticVertexCount(),
                        resource.getDynamicVertexCount()
                );
            } else {
                // Regular array rendering
                GL15.glDrawArrays(
                        resource.getPrimitiveType().getGLType(),
                        0,
                        resource.getStaticVertexCount()
                );
            }
        } finally {
            resource.unbind();
        }
    }

    /**
     * Render with custom parameters (offset and count)
     * Legacy method - prefer using renderWithOffsets for better control
     *
     * @param resource The vertex resource to render
     * @param first    First vertex/index to render
     * @param count    Number of vertices/indices to render
     */
    public static void render(VertexResource resource, int first, int count) {
        if (resource.hasIndices()) {
            // Use unified call for indexed rendering
            renderWithOffsets(
                    resource,
                    resource.getPrimitiveType(),
                    count,
                    first * 4L, // Convert to byte offset (4 bytes per int)
                    0,         // baseVertex = 0
                    1,         // single instance
                    0          // baseInstance = 0
            );
        } else {
            // Fallback to array rendering for non-indexed
            resource.bind();
            try {
                GL15.glDrawArrays(resource.getPrimitiveType().getGLType(), first, count);
            } finally {
                resource.unbind();
            }
        }
    }

    /**
     * Render with instancing and custom parameters
     * Legacy method - prefer using renderWithOffsets for better control
     *
     * @param resource      The vertex resource to render
     * @param primitiveMode OpenGL primitive mode
     * @param first         First vertex/index to render
     * @param count         Number of vertices/indices to render
     * @param instanceCount Number of instances to render
     */
    public static void renderInstanced(VertexResource resource, PrimitiveType primitiveMode,
                                       int first, int count, int instanceCount) {
        if (resource.hasIndices()) {
            // Use unified call for indexed instanced rendering
            renderWithOffsets(
                    resource,
                    primitiveMode,
                    count,
                    first * 4L, // Convert to byte offset
                    0,         // baseVertex = 0
                    instanceCount,
                    0          // baseInstance = 0
            );
        } else {
            // Fallback to array rendering for non-indexed instanced
            resource.bind();
            try {
                GL31.glDrawArraysInstanced(primitiveMode.getGLType(), first, count, instanceCount);
            } finally {
                resource.unbind();
            }
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
    public static IndexBufferResource.IndexType getOptimalIndexType(int maxVertexIndex) {
        return IndexBufferResource.IndexType.getOptimalType(maxVertexIndex);
    }

    /**
     * Get render statistics for debugging
     */
    public static RenderStats getRenderStats(VertexResource resource) {
        return new RenderStats(
                resource.getStaticVertexCount(),
                resource.getDynamicVertexCount(),
                resource.hasIndices() ? resource.getIndexBuffer().getIndexCount() : 0,
                resource.hasIndices(),
                resource.hasInstances(),
                resource.getDrawMode()
        );
    }

    /**
     * Render statistics for debugging and profiling
     * Updated to reflect unified rendering approach
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