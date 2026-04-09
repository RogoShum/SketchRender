package rogo.sketch.core.api.model;

import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.util.KeyId;

public record SharedGeometrySourceSnapshot(
        long sharedSourceRef,
        KeyId componentId,
        StructLayout format,
        PrimitiveType primitiveType,
        byte[] vertexData,
        int vertexCount,
        byte[] indexData,
        int indexCount
) {
    public SharedGeometrySourceSnapshot {
        sharedSourceRef = Math.max(sharedSourceRef, 0L);
        componentId = componentId != null ? componentId : BakedTypeMesh.BAKED_MESH;
        vertexData = vertexData != null ? vertexData.clone() : new byte[0];
        indexData = indexData != null ? indexData.clone() : new byte[0];
        vertexCount = Math.max(vertexCount, 0);
        indexCount = Math.max(indexCount, 0);
    }

    public boolean hasVertexData() {
        return format != null && vertexData.length > 0 && vertexCount > 0;
    }

    public boolean hasIndexData() {
        return indexData.length > 0 && indexCount > 0;
    }

    public int stride() {
        return format != null ? format.getStride() : 0;
    }
}

