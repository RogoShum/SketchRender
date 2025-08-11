package rogo.sketch.api;

import rogo.sketch.render.DrawCommand;
import rogo.sketch.render.data.filler.VertexFiller;

public interface SharedVertexProvider extends GraphicsInstance {
    boolean needsVertexUpdate();

    void fillVertexData(VertexFiller filler);

    DrawCommand getDrawCommand();
}