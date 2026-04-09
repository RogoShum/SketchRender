package rogo.sketch.core.backend;

import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.layout.StructLayout;

/**
 * Backend-owned geometry metadata exposed to core flow/model code.
 */
public interface BackendGeometryMetadata {
    PrimitiveType primitiveType();

    StructLayout vertexFormat();
}

