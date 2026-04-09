package rogo.sketch.core.data;

/**
 * Primitive topology description.
 * <p>
 * This type only describes how vertices are assembled. Index generation and
 * index submission are handled by explicit mesh/index policy.
 */
public enum PrimitiveType {
    POINTS(1, false, false),
    LINES(2, false, true),
    LINE_STRIP(2, true, false),
    LINE_LOOP(2, true, false),
    TRIANGLES(3, false, true),
    TRIANGLE_STRIP(3, true, false),
    TRIANGLE_FAN(3, true, false),
    QUADS(4, false, true);

    private final int verticesPerPrimitive;
    private final boolean connected;
    private final boolean supportsSorting;

    PrimitiveType(int verticesPerPrimitive, boolean connected, boolean supportsSorting) {
        this.verticesPerPrimitive = verticesPerPrimitive;
        this.connected = connected;
        this.supportsSorting = supportsSorting;
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
     * Check if this primitive type supports sorting
     * Only non-connected primitives can be sorted without breaking rendering order
     */
    public boolean supportsSorting() {
        return supportsSorting;
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

