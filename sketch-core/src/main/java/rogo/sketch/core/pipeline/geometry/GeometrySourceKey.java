package rogo.sketch.core.pipeline.geometry;

import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.util.KeyId;

public record GeometrySourceKey(
        KeyId sourceKind,
        long stableId,
        int vertexOffset,
        int indexOffset,
        int vertexCount,
        int indexCount
) {
    private static final KeyId EMPTY_KIND = KeyId.of("sketch:dynamic_geometry");
    private static final KeyId BAKED_KIND = KeyId.of("sketch:baked_geometry");
    private static final GeometrySourceKey EMPTY = new GeometrySourceKey(EMPTY_KIND, -1L, 0, 0, 0, 0);

    public static GeometrySourceKey empty() {
        return EMPTY;
    }

    public static GeometrySourceKey fromPreparedMesh(PreparedMesh mesh) {
        if (mesh == null) {
            return empty();
        }
        if (mesh instanceof BakedTypeMesh bakedMesh) {
            long sourceStableId = bakedMesh.getSourceResource() != null
                    && bakedMesh.getSourceResource().getComponent(BakedTypeMesh.BAKED_MESH) != null
                    ? bakedMesh.getSourceResource().getComponent(BakedTypeMesh.BAKED_MESH).getVboHandle()
                    : bakedMesh.getVAOHandle();
            return new GeometrySourceKey(
                    BAKED_KIND,
                    sourceStableId,
                    bakedMesh.getVertexOffset(),
                    bakedMesh.getIndexOffset(),
                    bakedMesh.getVertexCount(),
                    bakedMesh.getIndicesCount());
        }
        return new GeometrySourceKey(
                EMPTY_KIND,
                System.identityHashCode(mesh),
                mesh.getVertexOffset(),
                mesh.getIndexOffset(),
                mesh.getVertexCount(),
                mesh.getIndicesCount());
    }

    public boolean sharesSourceBuffers() {
        return stableId >= 0L && BAKED_KIND.equals(sourceKind);
    }

    public long sharedSourceId() {
        return sharesSourceBuffers() ? stableId : -1L;
    }
}
