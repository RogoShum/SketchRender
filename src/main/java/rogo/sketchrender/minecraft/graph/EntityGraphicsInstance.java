package rogo.sketchrender.minecraft.graph;

import net.minecraft.world.entity.Entity;
import rogo.sketchrender.minecraft.McRenderContext;
import rogo.sketchrender.render.GraphicsInstance;
import rogo.sketchrender.util.Identifier;

public class EntityGraphicsInstance extends GraphicsInstance<McRenderContext> {
    private final Entity entity;

    public EntityGraphicsInstance(Identifier identifier, Entity entity) {
        super(identifier);
        this.entity = entity;
    }

    @Override
    public void tick() {

    }

    @Override
    public boolean shouldDiscard() {
        return entity.isRemoved();
    }

    @Override
    public boolean shouldRender() {
        return true;
    }
}