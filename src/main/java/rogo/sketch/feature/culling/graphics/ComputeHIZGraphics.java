package rogo.sketch.feature.culling.graphics;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.util.KeyId;

import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;

public class ComputeHIZGraphics extends ComputeGraphics {
    private final boolean first;

    public ComputeHIZGraphics(KeyId keyId, boolean first) {
        super(keyId, (c) -> {
        }, (c, shader) -> {
            RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();

            int tileSizeX = 16;
            int tileSizeY = 16;
            int groupsX;
            int groupsY;

            if (first) {
                groupsX = (screen.width / 2 + tileSizeX - 1) / tileSizeX;
                groupsY = (screen.height / 2 + tileSizeY - 1) / tileSizeY;
            } else {
                groupsX = ((screen.width >> 4) / 2 + tileSizeX - 1) / tileSizeX;
                groupsY = ((screen.height >> 4) / 2 + tileSizeY - 1) / tileSizeY;
            }

            shader.dispatch(groupsX, groupsY, 1);
            shader.memoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        });
        this.first = first;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return CullingStateManager.anyCulling() && !CullingStateManager.CHECKING_CULL && CullingStateManager.continueUpdateDepth();
    }

    public boolean first() {
        return first;
    }
}