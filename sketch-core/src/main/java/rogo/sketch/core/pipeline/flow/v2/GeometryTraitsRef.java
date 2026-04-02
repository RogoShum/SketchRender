package rogo.sketch.core.pipeline.flow.v2;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;

public record GeometryTraitsRef(
        @Nullable PreparedMesh preparedMesh,
        GeometrySourceKey sourceKey,
        GeometryBatchKey geometryBatchKey,
        int vertexCount,
        int indexCount,
        boolean indexed
) {
    public GeometryTraitsRef {
        sourceKey = sourceKey != null ? sourceKey : GeometrySourceKey.empty();
        geometryBatchKey = geometryBatchKey != null
                ? geometryBatchKey
                : new GeometryBatchKey(GeometrySourceKey.empty(), null, null, null);
    }
}
