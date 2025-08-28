package rogo.sketch.feature.culling;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.vanilla.ShaderManager;

import java.util.ArrayList;
import java.util.List;

import static rogo.sketch.gui.ConfigScreen.u;
import static rogo.sketch.gui.ConfigScreen.v;

public class CullingRenderEvent {
    int textWidth;

    public void addString(List<String> list, String text) {
        list.add(text);
    }

    public void renderText(GuiGraphics guiGraphics, List<String> list, int width, int height) {
        textWidth = 0;
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < list.size(); ++i) {
            String text = list.get(i);
            textWidth = Math.max(font.width(text), textWidth);
            guiGraphics.drawString(font, text, (int) (width - (font.width(text) / 2f)), height + font.lineHeight * i, 16777215);
        }
    }

    @SubscribeEvent
    public void onOverlayRender(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }

        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HELMET.id())) {
            return;
        }

        if (CullingStateManager.DEBUG > 0) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Minecraft minecraft = Minecraft.getInstance();
            int width = minecraft.getWindow().getGuiScaledWidth() / 2;
            int height = 20;
            int widthScale = Math.max(80, textWidth / 2 + 20);

            List<String> monitorTexts = new ArrayList<>();

            if (CullingStateManager.FPS == 0) {
                CullingStateManager.FPS++;
            }

            int index = Minecraft.getInstance().fpsString.indexOf("fps");
            if (index != -1) {
                String extractedString = Minecraft.getInstance().fpsString.substring(0, index + 3);
                String fps = "FPS: " + extractedString;
                addString(monitorTexts, fps);
            }

            String cull = Component.translatable(SketchRender.MOD_ID + ".cull_entity").getString() + ": "
                    + (Config.getCullEntity() ? Component.translatable(SketchRender.MOD_ID + ".enable").getString() : Component.translatable(SketchRender.MOD_ID + ".disable").getString());
            addString(monitorTexts, cull);

            String cull_block_entity = Component.translatable(SketchRender.MOD_ID + ".cull_block_entity").getString() + ": "
                    + (Config.getCullBlockEntity() ? Component.translatable(SketchRender.MOD_ID + ".enable").getString() : Component.translatable(SketchRender.MOD_ID + ".disable").getString());
            addString(monitorTexts, cull_block_entity);

            String cull_chunk = Component.translatable(SketchRender.MOD_ID + ".cull_chunk").getString() + ": "
                    + (Config.getCullChunk() ? Component.translatable(SketchRender.MOD_ID + ".enable").getString() : Component.translatable(SketchRender.MOD_ID + ".disable").getString());
            addString(monitorTexts, cull_chunk);

            if (CullingStateManager.DEBUG > 1) {
                if (Config.doEntityCulling()) {
                    String entityCulling = Component.translatable(SketchRender.MOD_ID + ".entity_cull_count").getString() + ": " + CullingStateManager.ENTITY_CULLING + " / " + CullingStateManager.ENTITY_COUNT;
                    addString(monitorTexts, entityCulling);

                    String blockCulling = Component.translatable(SketchRender.MOD_ID + ".block_cull_count").getString() + ": " + CullingStateManager.BLOCK_CULLING + " / " + CullingStateManager.BLOCK_COUNT;
                    addString(monitorTexts, blockCulling);
                }
            }

            int heightOffset = minecraft.font.lineHeight * monitorTexts.size();
            int top = height;
            int bottom = height + heightOffset;
            int left = width + widthScale;
            int right = width - widthScale;

            float bgColor = 1.0f;
            float bgAlpha = 0.3f;
            BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            bufferbuilder.vertex(right - 1, bottom + 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(right - 1), v(bottom + 1)).endVertex();
            bufferbuilder.vertex(left + 1, bottom + 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(left + 1), v(bottom + 1)).endVertex();
            bufferbuilder.vertex(left + 1, top - 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(left + 1), v(top - 1)).endVertex();
            bufferbuilder.vertex(right - 1, top - 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(right - 1), v(top - 1)).endVertex();
            RenderSystem.setShaderTexture(0, Minecraft.getInstance().getMainRenderTarget().getColorTextureId());
            CullingStateManager.useShader(ShaderManager.REMOVE_COLOR_SHADER);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.1f);
            RenderSystem.disableBlend();
            RenderSystem.getModelViewStack().pushPose();
            RenderSystem.getModelViewStack().translate(0, 0, -1);
            RenderSystem.applyModelViewMatrix();
            BufferUploader.drawWithShader(bufferbuilder.end());
            RenderSystem.getModelViewStack().popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            bufferbuilder.vertex(right, bottom, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            bufferbuilder.vertex(left, bottom, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            bufferbuilder.vertex(left, top, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            bufferbuilder.vertex(right, top, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(bufferbuilder.end());
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            renderText(guiGraphics, monitorTexts, width, top);
            RenderSystem.enableDepthTest();
            Tesselator tessellator = Tesselator.getInstance();

            if (!CullingStateManager.CHECKING_TEXTURE)
                return;

            float screenScale = 1.0f;
            double windowScale = 0.4;
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            for (int i = 0; i < CullingStateManager.DEPTH_BUFFER_TARGET.length; ++i) {
                int scaledHeight = (int) (minecraft.getWindow().getGuiScaledHeight() * windowScale * screenScale);
                int scaledWidth = (int) (minecraft.getWindow().getGuiScaledWidth() * windowScale * screenScale);
                int offsetHeight = (int) ((1 - screenScale) * 2 * minecraft.getWindow().getGuiScaledHeight() * windowScale);
                bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(0.0D, minecraft.getWindow().getGuiScaledHeight() - offsetHeight, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(scaledWidth, minecraft.getWindow().getGuiScaledHeight() - offsetHeight, 0.0D).uv(1, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(scaledWidth, minecraft.getWindow().getGuiScaledHeight() - scaledHeight - offsetHeight, 0.0D).uv(1, 1).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(0.0D, minecraft.getWindow().getGuiScaledHeight() - scaledHeight - offsetHeight, 0.0D).uv(0.0F, 1).color(255, 255, 255, 255).endVertex();
                RenderSystem.setShaderTexture(0, CullingStateManager.DEPTH_BUFFER_TARGET[i].getColorTextureId());
                tessellator.end();
                screenScale *= 0.5f;
            }

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }
}