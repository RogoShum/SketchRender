package rogo.sketch.vanilla.graphics;

import net.minecraft.world.entity.Entity;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexDataBuilder;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

public class EntityGraphicsInstance extends MeshGraphics {
    private final Entity entity;

    public EntityGraphicsInstance(KeyId keyId, Entity entity) {
        super(keyId);
        this.entity = entity;
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
        return !entity.isAlive();
    }

    @Override
    public boolean shouldRender() {
        return false;
    }

    @Override
    public <C extends RenderContext> void afterDraw(C context) {

    }

    @Override
    public PreparedMesh getPreparedMesh() {
        return null;
    }

    @Override
    public void fillVertex(KeyId componentKey, VertexDataBuilder builder) {

    }
}