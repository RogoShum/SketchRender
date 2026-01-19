package rogo.sketch.feature.culling.graphics;

import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.util.KeyId;

import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class CopyCounterGraphics extends ComputeGraphics {

    public CopyCounterGraphics(KeyId keyId) {
        super(keyId, (c) -> {
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
        return true;
    }
}