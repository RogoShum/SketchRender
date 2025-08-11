package rogo.sketch.render;

import rogo.sketch.render.data.format.DataFormat;

public record RenderParameter(
        DataFormat dataFormat,          // Vertex data format
        int primitiveType,              // OpenGL primitive type (GL_TRIANGLES, etc.)
        int usage,                      // Buffer usage hint (GL_STATIC_DRAW, etc.)
        boolean enableIndexBuffer,      // Whether to enable index buffer
        boolean enableSorting          // Whether to enable vertex sorting
) {

    public static final RenderParameter EMPTY = new RenderParameter(DataFormat.builder("EMPTY").build(), -1, -1, false, false);
}