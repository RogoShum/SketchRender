package rogo.sketch.core.data;

/**
 * Formal mesh/index policy used by resource loaders and raster parameters.
 * <p>
 * The primitive topology describes how vertices are interpreted. The index mode
 * describes whether the draw is non-indexed, uses explicit local indices, or
 * relies on generated topology indices.
 */
public enum MeshIndexMode {
    NONE(false, false),
    EXPLICIT_LOCAL(true, false),
    GENERATED(true, true);

    private final boolean usesIndexBuffer;
    private final boolean generated;

    MeshIndexMode(boolean usesIndexBuffer, boolean generated) {
        this.usesIndexBuffer = usesIndexBuffer;
        this.generated = generated;
    }

    public boolean usesIndexBuffer() {
        return usesIndexBuffer;
    }

    public boolean isGenerated() {
        return generated;
    }
}

