package rogo.sketch.render;

import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResource;

/**
 * Render command that pairs with vertex resources
 */
public record DrawCommand(
        int primitiveType,      // OpenGL primitive type (GL_TRIANGLES, etc.)
        int drawMode,          // Draw mode (arrays, elements, instanced, etc.)
        int instanceCount,     // Number of instances (for instanced rendering)
        boolean useIndexBuffer, // Whether to use index buffer
        boolean enableSorting   // Whether to enable vertex sorting
) {

    public static final DrawCommand EMPTY = new DrawCommand(-1, -1, -1, false, false);

    /**
     * Execute this draw command on a vertex resource
     */
    public void execute(VertexResource resource) {
        switch (drawMode) {
            case DrawMode.ARRAYS -> {
                if (instanceCount > 1) {
                    VertexRenderer.renderInstanced(resource, resource.getPrimitiveType(), 0, resource.getStaticVertexCount(), instanceCount);
                } else {
                    VertexRenderer.render(resource, 0, resource.getStaticVertexCount());
                }
            }
            case DrawMode.ELEMENTS -> {
                if (instanceCount > 1) {
                    VertexRenderer.renderInstanced(resource, resource.getPrimitiveType(), 0,
                            resource.hasIndices() ? resource.getIndexBuffer().getIndexCount() : resource.getStaticVertexCount(),
                            instanceCount);
                } else {
                    VertexRenderer.render(resource);
                }
            }
            case DrawMode.INSTANCED -> {
                VertexRenderer.renderInstanced(resource, resource.getPrimitiveType(), 0,
                        resource.hasIndices() ? resource.getIndexBuffer().getIndexCount() : resource.getStaticVertexCount(),
                        instanceCount);
            }
        }
    }

    /**
     * Draw modes
     */
    public static class DrawMode {
        public static final int ARRAYS = 0;
        public static final int ELEMENTS = 1;
        public static final int INSTANCED = 2;
    }
}