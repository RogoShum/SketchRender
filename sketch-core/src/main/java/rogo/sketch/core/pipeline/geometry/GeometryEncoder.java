package rogo.sketch.core.pipeline.geometry;

import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.vertex.VertexResourceManager;

public interface GeometryEncoder<G extends MeshBasedGraphics> {
    GeometryEncodeResult inspect(G graphics);

    void encodeInstance(G graphics, VertexResourceManager.BuilderPair[] builders);
}
