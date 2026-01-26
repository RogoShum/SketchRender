package rogo.sketch.core.data;

import org.lwjgl.opengl.GL11;

/**
 * OpenGL primitive types with automatic tessellation support
 */
public enum PrimitiveType {
    POINTS(GL11.GL_POINTS, 1, false, false, false),
    LINES(GL11.GL_LINES, 2, false, true, true),        // 2 vertices -> 4 vertices (quad-like)
    LINE_STRIP(GL11.GL_LINE_STRIP, 2, true, false, false),    // Connected, no sorting
    LINE_LOOP(GL11.GL_LINE_LOOP, 2, true, false, false),      // Connected, no sorting
    TRIANGLES(GL11.GL_TRIANGLES, 3, false, true, true),       // Independent triangles, can sort
    TRIANGLE_STRIP(GL11.GL_TRIANGLE_STRIP, 3, true, false, false), // Connected, no sorting
    TRIANGLE_FAN(GL11.GL_TRIANGLE_FAN, 3, true, false, false),     // Connected, no sorting
    QUADS(GL11.GL_TRIANGLES, 4, false, true, true);           // Quads tessellated to triangles

    private final int glType;
    private final int verticesPerPrimitive;
    private final boolean connected;
    private final boolean requiresIndexBuffer;
    private final boolean supportsSorting;

    PrimitiveType(int glType, int verticesPerPrimitive, boolean connected, boolean requiresIndexBuffer, boolean supportsSorting) {
        this.glType = glType;
        this.verticesPerPrimitive = verticesPerPrimitive;
        this.connected = connected;
        this.requiresIndexBuffer = requiresIndexBuffer;
        this.supportsSorting = supportsSorting;
    }

    /**
     * Get the OpenGL primitive type constant
     */
    public int glType() {
        return glType;
    }

    /**
     * Get the number of vertices required per primitive
     */
    public int getVerticesPerPrimitive() {
        return verticesPerPrimitive;
    }

    /**
     * Check if primitives are connected (like strips/fans)
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Check if this primitive type requires tessellation
     */
    public boolean requiresIndexBuffer() {
        return requiresIndexBuffer;
    }

    /**
     * Check if this primitive type supports sorting
     * Only non-connected primitives can be sorted without breaking rendering order
     */
    public boolean supportsSorting() {
        return supportsSorting;
    }

    /**
     * Calculate the number of indices needed for tessellation
     *
     * @param vertexCount The number of vertices to tessellate
     * @return The number of indices needed after tessellation
     */
    public int calculateIndexCount(int vertexCount) {
        return switch (this) {
            case QUADS -> {
                int quadCount = vertexCount / 4;
                yield quadCount * 6;
                // Each quad (4 vertices) becomes 2 triangles (6 indices)
            }
            case LINES -> {
                int lineCount = vertexCount / 2;
                yield lineCount * 6;
                // Each line (2 vertices) expands to quad (4 vertices) -> 2 triangles (6 indices)
            }
            case TRIANGLES ->
                // Each triangle uses 3 indices
                    vertexCount;
            case POINTS -> vertexCount;
            default -> vertexCount; // For strips/fans, assume no tessellation
        };
    }

    /**
     * Generate indices for tessellation
     *
     * @param vertexCount The number of vertices
     * @return Array of indices for tessellated primitives
     */
    public int[] generateIndices(int vertexCount) {
        switch (this) {
            case QUADS: {
                int quadCount = vertexCount / 4;
                int[] indices = new int[quadCount * 6];
                for (int i = 0; i < quadCount; i++) {
                    int quadStart = i * 4;
                    int indexStart = i * 6;

                    // First triangle: 0, 1, 2
                    indices[indexStart] = quadStart;
                    indices[indexStart + 1] = quadStart + 1;
                    indices[indexStart + 2] = quadStart + 2;

                    // Second triangle: 2, 3, 0
                    indices[indexStart + 3] = quadStart + 2;
                    indices[indexStart + 4] = quadStart + 3;
                    indices[indexStart + 5] = quadStart;
                }
                return indices;
            }
            case LINES: {
                int lineCount = vertexCount / 2;
                int[] indices = new int[lineCount * 6];
                for (int i = 0; i < lineCount; i++) {
                    int lineStart = i * 4;
                    int indexStart = i * 6;


                    indices[indexStart] = lineStart;
                    indices[indexStart + 1] = lineStart + 1;
                    indices[indexStart + 2] = lineStart + 2;

                    indices[indexStart + 3] = lineStart + 3;
                    indices[indexStart + 4] = lineStart + 2;
                    indices[indexStart + 5] = lineStart + 1;
                }
                return indices;
            }
            default: {
                int[] indices = new int[vertexCount];
                for (int i = 0; i < vertexCount; i++) {
                    indices[i] = i;
                }
                return indices;
            }
        }
    }

    /**
     * Validate if the vertex count is valid for this primitive type
     */
    public boolean isValidVertexCount(int vertexCount) {
        if (vertexCount == 0) return true;

        return switch (this) {
            case POINTS -> true;
            case LINES -> vertexCount % 2 == 0;
            case TRIANGLES -> vertexCount % 3 == 0;
            case QUADS -> vertexCount % 4 == 0;
            case LINE_STRIP, LINE_LOOP -> vertexCount >= 2;
            case TRIANGLE_STRIP, TRIANGLE_FAN -> vertexCount >= 3;
            default -> false;
        };
    }
}