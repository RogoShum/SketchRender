package rogo.sketch.render.instance;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.api.graphics.MeshProvider;
import rogo.sketch.util.KeyId;

public abstract class MeshGraphics implements Graphics, MeshProvider {
    private final KeyId id;

    public MeshGraphics(KeyId keyId) {
        this.id = keyId;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }
}