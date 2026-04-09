package rogo.sketch.core.pipeline.geometry;

import rogo.sketch.core.api.graphics.InstanceVertexEncoder;
import rogo.sketch.core.api.graphics.RasterGraphics;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.model.DynamicMesh;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

public final class RasterGeometryEncoder implements GeometryEncoder<RasterGraphics> {
    @Override
    public GeometryEncodeResult inspect(RasterGraphics graphics) {
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
    public void encodeDynamicComponents(
            RasterGraphics graphics,
            VertexBufferKey vertexBufferKey,
            GeometryResourceCoordinator.BuilderPair[] builders) {
        if (graphics == null || vertexBufferKey == null || builders == null) {
            return;
        }
        PreparedMesh preparedMesh = graphics.getPreparedMesh();
        DynamicMesh dynamicMesh = preparedMesh instanceof DynamicMesh cast ? cast : null;

        ComponentSpec[] dynamicComponents = vertexBufferKey.dynamicComponents();
        int count = Math.min(dynamicComponents.length, builders.length);
        for (int i = 0; i < count; i++) {
            ComponentSpec componentSpec = dynamicComponents[i];
            GeometryResourceCoordinator.BuilderPair pair = builders[i];
            if (componentSpec == null || pair == null || pair.builder() == null) {
                continue;
            }
            if (graphics instanceof InstanceVertexEncoder instanceVertexEncoder) {
                instanceVertexEncoder.writeInstanceVertex(pair.key(), pair.builder());
                continue;
            }
            if (dynamicMesh != null && dynamicMesh.generator() != null) {
                dynamicMesh.generator().accept(pair.builder());
            }
        }
    }
}

