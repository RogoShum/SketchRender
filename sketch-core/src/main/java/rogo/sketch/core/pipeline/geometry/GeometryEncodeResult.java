package rogo.sketch.core.pipeline.geometry;

public record GeometryEncodeResult(
        GeometrySourceKey sourceKey,
        int vertexCount,
        int indexCount,
        boolean indexed
) {
    public GeometryEncodeResult {
        sourceKey = sourceKey != null ? sourceKey : GeometrySourceKey.empty();
    }
}
