package rogo.sketch.core.instance;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.MeshProvider;
import rogo.sketch.core.util.KeyId;

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