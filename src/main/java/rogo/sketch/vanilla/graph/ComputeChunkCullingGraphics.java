package rogo.sketch.vanilla.graph;

import rogo.sketch.Config;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.util.Identifier;

import static org.lwjgl.opengl.GL42.GL_ATOMIC_COUNTER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ComputeChunkCullingGraphics extends ComputeGraphics {

    public ComputeChunkCullingGraphics(Identifier identifier) {
        super(identifier, (c) -> {
        }, (c, shader) -> {
            shader.dispatch(MeshResource.orderedRegionSize, 3, 1);
            shader.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT |
                    GL_ATOMIC_COUNTER_BARRIER_BIT |
                    GL_COMMAND_BARRIER_BIT);
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