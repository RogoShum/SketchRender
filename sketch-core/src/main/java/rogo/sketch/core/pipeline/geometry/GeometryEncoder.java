package rogo.sketch.core.pipeline.geometry;

import rogo.sketch.core.api.graphics.RasterGraphics;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

public interface GeometryEncoder<G extends RasterGraphics> {
    GeometryEncodeResult inspect(G graphics);

    void encodeDynamicComponents(
            G graphics,
            VertexBufferKey vertexBufferKey,
            GeometryResourceCoordinator.BuilderPair[] builders);
}
