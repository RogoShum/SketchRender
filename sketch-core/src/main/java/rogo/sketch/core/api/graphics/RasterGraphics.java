package rogo.sketch.core.api.graphics;

/**
 * Formal raster graphics contract for the V2 pipeline. Raster graphics always
 * provide a prepared mesh and a render descriptor.
 */
public interface RasterGraphics extends Graphics, PreparedMeshProvider, RenderDescriptorProvider {
}

