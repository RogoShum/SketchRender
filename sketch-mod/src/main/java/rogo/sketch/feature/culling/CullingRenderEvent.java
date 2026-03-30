package rogo.sketch.feature.culling;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CullingRenderEvent {
    @SubscribeEvent
    public void onOverlayRender(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HELMET.id())) {
            return;
        }
        if (CullingStateManager.DEBUG <= 0 || !CullingStateManager.CHECKING_TEXTURE || CullingStateManager.DEPTH_BUFFER_TARGET == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        float windowScale = 0.7f * (minecraft.getWindow().getGuiScaledWidth() / (float) minecraft.getWindow().getWidth());
        int scaledHeight = (int) (CullingStateManager.DEPTH_BUFFER_TARGET.height * windowScale);
        int scaledWidth = (int) (CullingStateManager.DEPTH_BUFFER_TARGET.width * windowScale);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(0.0D, minecraft.getWindow().getGuiScaledHeight(), 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(scaledWidth, minecraft.getWindow().getGuiScaledHeight(), 0.0D).uv(1, 0.0F).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(scaledWidth, minecraft.getWindow().getGuiScaledHeight() - scaledHeight, 0.0D).uv(1, 1).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(0.0D, minecraft.getWindow().getGuiScaledHeight() - scaledHeight, 0.0D).uv(0.0F, 1).color(255, 255, 255, 255).endVertex();
        RenderSystem.setShaderTexture(0, CullingStateManager.DEPTH_BUFFER_TARGET.getColorTextureId());
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
