package rogo.sketch.core.api.graphics;

import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.util.KeyId;

/**
 * Optional capability for raster graphics that need to populate dynamic vertex
 * components for the current raster parameter.
 * <p>
 * Despite the historical name, this is not limited to instanced attributes. It
 * may also be used for other dynamic component streams that are authored by the
 * graphics itself rather than baked into the prepared mesh.
 * </p>
 */
public interface InstanceVertexEncoder {
    void writeInstanceVertex(KeyId componentKey, VertexRecordWriter writer);
}

