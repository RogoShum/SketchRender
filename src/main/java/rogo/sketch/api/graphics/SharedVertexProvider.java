package rogo.sketch.api.graphics;

import rogo.sketch.render.pipeline.DrawCommand;
import rogo.sketch.render.data.filler.VertexFiller;

public interface SharedVertexProvider extends GraphicsInstance {
    boolean needsVertexUpdate();

    void fillVertexData(VertexFiller filler);

    DrawCommand getDrawCommand();
}