package rogo.sketch.feature.culling.graphics;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import rogo.sketch.SketchRender;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.render.pipeline.PartialRenderSetting;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.KeyId;

import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;

public class ComputeHIZGraphics extends ComputeGraphics {
    private final ResourceReference<PartialRenderSetting> firstRenderSetting = GraphicsResourceManager.getInstance().getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID,"hierarchy_depth_buffer_first"));
    private final ResourceReference<PartialRenderSetting> secRenderSetting = GraphicsResourceManager.getInstance().getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID,"hierarchy_depth_buffer_second"));
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
    public PartialRenderSetting getPartialRenderSetting() {
        if (first) {
            if (firstRenderSetting.isAvailable()) {
                return firstRenderSetting.get();
            }
        } else {
            if (secRenderSetting.isAvailable()) {
                return secRenderSetting.get();
            }
        }

        return null;
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