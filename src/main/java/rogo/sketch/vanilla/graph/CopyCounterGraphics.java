package rogo.sketch.vanilla.graph;

import rogo.sketch.Config;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.util.Identifier;

import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class CopyCounterGraphics extends ComputeGraphics {

    public CopyCounterGraphics(Identifier identifier) {
        super(identifier, (c) -> {
        }, (c, shader) -> {
            shader.dispatch(1, 1, 1);
            shader.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        });
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return Config.getCullEntity() && CullingStateManager.ENTITY_CULLING_MASK != null;
    }
}