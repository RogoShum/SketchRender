package rogo.sketch.core.pipeline.geometry;

import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.model.DynamicMesh;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

public final class RasterGeometryEncoder {
    public GeometryEncodeResult inspect(StageEntityView.Entry entry) {
        if (entry == null) {
            return new GeometryEncodeResult(GeometrySourceKey.empty(), 0, 0, false);
        }
        GeometrySourceKey sourceKey = GeometrySourceKey.fromPreparedMesh(entry.preparedMesh());
        return new GeometryEncodeResult(
                sourceKey,
                sourceKey.vertexCount(),
                sourceKey.indexCount(),
                sourceKey.indexCount() > 0);
    }

    public void encodeDynamicComponents(
            StageEntityView.Entry entry,
            VertexBufferKey vertexBufferKey,
            GeometryResourceCoordinator.BuilderPair[] builders) {
        if (entry == null || vertexBufferKey == null || builders == null) {
            return;
        }
        PreparedMesh preparedMesh = entry.preparedMesh();
        DynamicMesh dynamicMesh = preparedMesh instanceof DynamicMesh cast ? cast : null;
        GraphicsBuiltinComponents.InstanceVertexAuthoringComponent instanceVertexAuthoring = entry.instanceVertexAuthoring();

        ComponentSpec[] dynamicComponents = vertexBufferKey.dynamicComponents();
        int count = Math.min(dynamicComponents.length, builders.length);
        for (int i = 0; i < count; i++) {
            ComponentSpec componentSpec = dynamicComponents[i];
            GeometryResourceCoordinator.BuilderPair pair = builders[i];
            if (componentSpec == null || pair == null || pair.builder() == null) {
                continue;
            }
            if (instanceVertexAuthoring != null && instanceVertexAuthoring.authoring() != null) {
                instanceVertexAuthoring.authoring().writeInstanceVertex(pair.key(), pair.builder());
                continue;
            }
            if (dynamicMesh != null && dynamicMesh.generator() != null) {
                dynamicMesh.generator().accept(pair.builder());
            }
        }
    }
}

