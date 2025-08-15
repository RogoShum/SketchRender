package rogo.sketch.vanilla.graph;

import net.minecraft.world.entity.Entity;
import rogo.sketch.render.DrawCommand;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.instance.SharedVertexGraphics;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.util.Identifier;

public class EntityGraphicsInstance extends SharedVertexGraphics {
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
    public <C extends RenderContext> void tick(C context) {

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
    public boolean needsVertexUpdate() {
        return false;
    }

    @Override
    public void fillVertexData(VertexFiller filler) {

    }

    @Override
    public DrawCommand getDrawCommand() {
        return null;
    }
}