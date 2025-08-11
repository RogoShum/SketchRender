package rogo.sketch.api;

import rogo.sketch.render.vertex.VertexResourcePair;

import java.util.List;

public interface IndependentVertexProvider extends GraphicsInstance {
    boolean needsVertexUpdate();

    void fillVertexData();

    List<VertexResourcePair> getVertexResources();
}