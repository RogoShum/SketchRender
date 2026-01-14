package rogo.sketch.render.instance;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.api.graphics.MeshProvider;
import rogo.sketch.util.Identifier;

public abstract class MeshGraphics implements Graphics, MeshProvider {
    private final Identifier id;

    public MeshGraphics(Identifier identifier) {
        this.id = identifier;
    }

    @Override
    public Identifier getIdentifier() {
        return id;
    }
}