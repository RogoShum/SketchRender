package rogo.sketch.render.instance;

import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.util.Identifier;

public class ScreenSpaceGraphics extends MeshGraphics {

    public ScreenSpaceGraphics(Identifier identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return false;
    }

    @Override
    public PreparedMesh getPreparedMesh() {
        return null;
    }
}