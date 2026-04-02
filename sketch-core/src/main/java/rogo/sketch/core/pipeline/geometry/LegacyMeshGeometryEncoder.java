package rogo.sketch.core.pipeline.geometry;

import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.vertex.VertexResourceManager;

public final class LegacyMeshGeometryEncoder implements GeometryEncoder<MeshBasedGraphics> {
    @Override
    public GeometryEncodeResult inspect(MeshBasedGraphics graphics) {
        if (graphics == null) {
            return new GeometryEncodeResult(GeometrySourceKey.empty(), 0, 0, false);
        }
        GeometrySourceKey sourceKey = GeometrySourceKey.fromPreparedMesh(graphics.getPreparedMesh());
        return new GeometryEncodeResult(
                sourceKey,
                sourceKey.vertexCount(),
                sourceKey.indexCount(),
                sourceKey.indexCount() > 0);
    }

    @Override
    public void encodeInstance(MeshBasedGraphics graphics, VertexResourceManager.BuilderPair[] builders) {
        if (graphics == null || builders == null) {
            return;
        }
        for (VertexResourceManager.BuilderPair pair : builders) {
            graphics.fillVertex(pair.key(), pair.builder());
        }
    }
}
