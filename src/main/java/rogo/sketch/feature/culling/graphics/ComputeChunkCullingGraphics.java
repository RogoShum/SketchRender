package rogo.sketch.feature.culling.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL13;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.util.Identifier;

import static org.lwjgl.opengl.GL42.GL_ATOMIC_COUNTER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ComputeChunkCullingGraphics extends ComputeGraphics {

    public ComputeChunkCullingGraphics(Identifier identifier) {
        super(identifier, (c) -> {
        }, (c, shader) -> {
            shader.dispatch(MeshResource.ORDERED_REGION_SIZE, 3, 1);
            shader.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT |
                    GL_ATOMIC_COUNTER_BARRIER_BIT |
                    GL_COMMAND_BARRIER_BIT);

            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        });
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return true;
    }
}