package rogo.sketch.render.instance;

import rogo.sketch.api.graphics.SharedVertexProvider;
import rogo.sketch.util.Identifier;

public abstract class SharedVertexGraphics implements SharedVertexProvider {
    private final Identifier id;

    public SharedVertexGraphics(Identifier identifier) {
        this.id = identifier;
    }

    @Override
    public Identifier getIdentifier() {
        return id;
    }
}