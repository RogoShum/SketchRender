package rogo.sketch.api.graphics;

import rogo.sketch.render.data.filler.VertexFiller;

public interface VertexDataProvider {
    void fillVertexData(VertexFiller filler);
}