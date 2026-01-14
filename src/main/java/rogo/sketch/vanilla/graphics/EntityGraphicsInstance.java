package rogo.sketch.vanilla.graphics;

import net.minecraft.world.entity.Entity;
import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.render.instance.MeshGraphics;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.Identifier;

public class EntityGraphicsInstance extends MeshGraphics {
    private final Entity entity;

    public EntityGraphicsInstance(Identifier identifier, Entity entity) {
        super(identifier);
        this.entity = entity;
    }


    @Override
    public boolean shouldTick() {
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
}