package rogo.sketch.core.instance;

import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.util.KeyId;

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
    public boolean tickable() {
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

    @Override
    public void fillVertex(KeyId componentKey, VertexStreamBuilder builder) {

    }
}