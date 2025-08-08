package rogo.sketch.vanilla.graph;

import net.minecraft.world.entity.Entity;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.render.GraphicsInstance;
import rogo.sketch.util.Identifier;

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
    public void fillBuffers() {

    }

    @Override
    public boolean shouldDiscard() {
        return entity.isRemoved();
    }

    @Override
    public boolean shouldRender() {
        return true;
    }

    @Override
    public void render(McRenderContext context) {

    }
}