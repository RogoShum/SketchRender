package rogo.sketch.render.instance;

import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.render.pipeline.PartialRenderSetting;
import rogo.sketch.util.KeyId;

public class ScreenSpaceGraphics extends MeshGraphics {

    public ScreenSpaceGraphics(KeyId keyId) {
        super(keyId);
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        return null;
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