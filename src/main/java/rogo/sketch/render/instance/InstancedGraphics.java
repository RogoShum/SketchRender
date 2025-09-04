package rogo.sketch.render.instance;

import rogo.sketch.api.graphics.GraphicsInstance;
import rogo.sketch.api.graphics.IndependentVertexProvider;
import rogo.sketch.util.Identifier;

public abstract class InstancedGraphics implements GraphicsInstance, IndependentVertexProvider {
    private final Identifier id;

    public InstancedGraphics(Identifier identifier) {
        this.id = identifier;
    }

    @Override
    public Identifier getIdentifier() {
        return id;
    }
}