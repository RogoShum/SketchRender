package rogo.sketch.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import rogo.sketch.SketchRender;
import rogo.sketch.Config;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.vanilla.ShaderManager;
import rogo.sketch.util.GLFeatureChecker;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ConfigScreen extends Screen {
    private boolean release = false;
    int heightScale;
    int textWidth;

    public ConfigScreen(Component titleIn) {
        super(titleIn);
        heightScale = (int) (Minecraft.getInstance().font.lineHeight * 2f + 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static float u(int width) {
        return (float) width / Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static float v(int height) {
        return 1.0f - ((float) height / (Minecraft.getInstance().getWindow().getGuiScaledHeight()));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth() / 2;
        int widthScale = textWidth / 2 + 15;
        int right = width - widthScale;
        int left = width + widthScale;
        int bottom = (int) (minecraft.getWindow().getGuiScaledHeight() * 0.8) + 20;
        int top = bottom - heightScale * children().size() - 10;

        float bgColor = 1.0f;
        float bgAlpha = 0.3f;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.1f);
        CullingStateManager.useShader(ShaderManager.REMOVE_COLOR_SHADER);
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
        BufferUploader.drawWithShader(bufferbuilder.end());
        bgAlpha = 1.0f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(right, bottom, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(left, bottom, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(left, top, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(right, top, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
        BufferUploader.drawWithShader(bufferbuilder.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0f);
    }

    @Override
    public boolean keyPressed(int p_96552_, int p_96553_, int p_96554_) {
        if (this.minecraft.options.keyInventory.matches(p_96552_, p_96553_)) {
            this.onClose();
            return true;
        } else if (this.minecraft.options.keyPlayerList.matches(p_96552_, p_96553_)) {
            this.onClose();
            return true;
        } else {
            return super.keyPressed(p_96552_, p_96553_, p_96554_);
        }
    }

    @Override
    public boolean keyReleased(int p_94715_, int p_94716_, int p_94717_) {
        if (SketchRender.CONFIG_KEY.matches(p_94715_, p_94716_)) {
            if (release) {
                this.onClose();
                return true;
            } else {
                release = true;
            }
        }
        return super.keyReleased(p_94715_, p_94716_, p_94717_);
    }

    @Override
    protected void init() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            onClose();
            return;
        }

        if (player.getName().getString().equals("Dev")) {
            addConfigButton(() -> CullingStateManager.checkCulling, (b) -> CullingStateManager.checkCulling = b, () -> Component.literal("Debug"))
                    .setDetailMessage(() -> Component.translatable(SketchRender.MOD_ID + ".detail.debug"));

            addConfigButton(() -> CullingStateManager.checkTexture, (b) -> CullingStateManager.checkTexture = b, () -> Component.literal("Check Texture"))
                    .setDetailMessage(() -> Component.translatable(SketchRender.MOD_ID + ".detail.check_texture"));
        }

        addConfigButton(Config::shouldComputeShader, Config::setComputeShader, () -> Component.translatable(SketchRender.MOD_ID + ".compute_shader"))
                .setDetailMessage(() -> Component.translatable(SketchRender.MOD_ID + ".detail.compute_shader"));

        addConfigButton(() -> Config.getDepthUpdateDelay() / 10d, (value) -> {
            double step = 0.5;
            double formatted = Math.round(value * 10 / step) * step;
            Config.setDepthUpdateDelay(formatted);
            return Math.max(formatted * 0.1, 0.1f);
        }, (value) -> {
            double step = 0.5;
            double formatted = Math.round(value * 10 / step) * step;
            return String.valueOf(formatted);
        }, () -> Component.translatable(SketchRender.MOD_ID + ".culling_map_update_delay"))
                .setDetailMessage(() -> Component.translatable(SketchRender.MOD_ID + ".detail.culling_map_update_delay"));

        addConfigButton(Config::getAutoDisableAsync, Config::setAutoDisableAsync, () -> Component.translatable(SketchRender.MOD_ID + ".auto_shader_async"))
                .setDetailMessage(() -> Component.translatable(SketchRender.MOD_ID + ".detail.auto_shader_async"));

        addConfigButton(() -> Config.getCullChunk() && SketchRender.hasSodium(), Config::getAsyncChunkRebuild, Config::setAsyncChunkRebuild, () -> Component.translatable(SketchRender.MOD_ID + ".async"))
                .setDetailMessage(() -> {
                    if (!SketchRender.hasSodium()) {
                        return Component.translatable(SketchRender.MOD_ID + ".detail.sodium");
                    } else
                        return Component.translatable(SketchRender.MOD_ID + ".detail.async");
                });
        addConfigButton(Config::getCullChunk, Config::setCullChunk, () -> Component.translatable(SketchRender.MOD_ID + ".cull_chunk"))
                .setDetailMessage(() -> {
                    if (GLFeatureChecker.supportsIndirectDrawCount()) {
                        return Component.translatable(SketchRender.MOD_ID + ".detail.cull_chunk");
                    } else {
                        return Component.translatable(SketchRender.MOD_ID + ".detail.gl46");
                    }
                });
        addConfigButton(Config::getCullBlockEntity, Config::setCullBlockEntity, () -> Component.translatable(SketchRender.MOD_ID + ".cull_block_entity"))
                .setDetailMessage(() -> {
                    if (GLFeatureChecker.supportsPersistentMapping()) {
                        return Component.translatable(SketchRender.MOD_ID + ".detail.cull_block_entity");
                    } else {
                        return Component.translatable(SketchRender.MOD_ID + ".detail.gl44");
                    }
                });
        addConfigButton(Config::getCullEntity, Config::setCullEntity, () -> Component.translatable(SketchRender.MOD_ID + ".cull_entity"))
                .setDetailMessage(() -> {
                    if (GLFeatureChecker.supportsPersistentMapping()) {
                        return Component.translatable(SketchRender.MOD_ID + ".detail.cull_entity");
                    } else {
                        return Component.translatable(SketchRender.MOD_ID + ".detail.gl44");
                    }
                });

        super.init();
    }

    public NeatButton addConfigButton(Supplier<Boolean> getter, Consumer<Boolean> setter, Supplier<Component> displayText) {
        int width = 150;
        int x = this.width / 2 - width / 2;
        NeatButton button = new NeatButton(x, (int) ((height * 0.8) - heightScale * children().size()), width, 14
                , getter, setter, displayText);
        this.addWidget(button);
        this.textWidth = Math.max(Math.max(width, font.width(displayText.get()) + 40), this.textWidth);
        button.setTextWidthGetter(() -> this.textWidth);
        return button;
    }

    public NeatButton addConfigButton(Supplier<Boolean> enable, Supplier<Boolean> getter, Consumer<Boolean> setter, Supplier<Component> displayText) {
        int width = 150;
        int x = this.width / 2 - width / 2;
        NeatButton button = new NeatButton(x, (int) ((height * 0.8) - heightScale * children().size()), width, 14
                , enable, getter, setter, displayText);
        this.addWidget(button);
        this.textWidth = Math.max(Math.max(width, font.width(displayText.get()) + 40), this.textWidth);
        button.setTextWidthGetter(() -> this.textWidth);
        return button;
    }

    public NeatSliderButton addConfigButton(Supplier<Double> getter, Function<Double, Double> setter, Function<Double, String> display, Supplier<MutableComponent> displayText) {
        int width = 150;
        int x = this.width / 2 - width / 2;
        NeatSliderButton button = new NeatSliderButton(x, (int) ((height * 0.8) - heightScale * children().size()), width, 14
                , getter, setter, display, displayText);
        this.addWidget(button);
        this.textWidth = Math.max(Math.max(width, font.width(displayText.get()) + 40), this.textWidth);
        button.setTextWidthGetter(() -> this.textWidth);
        return button;
    }

    @Override
    public boolean mouseDragged(double p_94699_, double p_94700_, int p_94701_, double p_94702_, double p_94703_) {
        return super.mouseDragged(p_94699_, p_94700_, p_94701_, p_94702_, p_94703_);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        List<? extends GuiEventListener> children = children();

        for (GuiEventListener button : children) {
            if (button instanceof AbstractWidget b)
                b.render(guiGraphics, mouseX, mouseY, partialTicks);
        }

        for (GuiEventListener button : children) {
            Component details = null;
            if (button instanceof NeatButton b) {
                details = b.getDetails();
            }
            if (button instanceof NeatSliderButton b) {
                details = b.getDetails();
            }
            if (details != null) {
                renderButtonDetails(guiGraphics, details);
            }
        }
    }

    private void renderButtonDetails(GuiGraphics guiGraphics, Component details) {
        String[] parts = details.getString().split("\\n");
        int partHeight = 0;
        int textWidth = Math.min(minecraft.getWindow().getScreenWidth(), 202);
        int x = minecraft.getWindow().getGuiScaledWidth() / 2 - (textWidth) / 2 + 2;
        for (String part : parts) {
            part = part.replace("warn:", "");
            List<FormattedCharSequence> text = Minecraft.getInstance().font.split(Component.literal(part), textWidth);
            partHeight += text.isEmpty() ? minecraft.font.lineHeight / 2 : text.size() * minecraft.font.lineHeight + minecraft.font.lineHeight / 4;
        }

        partHeight = 0;
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().translate(0, 0, 1);
        RenderSystem.applyModelViewMatrix();
        for (String part : parts) {
            boolean red = part.contains("warn:");
            if (red)
                part = part.replace("warn:", "");
            List<FormattedCharSequence> text = Minecraft.getInstance().font.split(Component.literal(part), textWidth);
            for (int row = 0; row < text.size(); ++row) {
                guiGraphics.drawString(minecraft.font, text.get(row), x, 4 + partHeight + row * minecraft.font.lineHeight,
                        red ? FastColor.ARGB32.color(255, 170, 0, 0) : FastColor.ARGB32.color(255, 255, 255, 255));
            }
            partHeight += text.isEmpty() ? minecraft.font.lineHeight / 2 : text.size() * minecraft.font.lineHeight + minecraft.font.lineHeight / 4;
        }

        int width = minecraft.getWindow().getGuiScaledWidth() / 2;
        int widthScale = textWidth / 2 + 4;
        int right = width - widthScale;
        int left = width + widthScale;
        int bottom = partHeight + 4;
        int top = 2;

        float bgColor = 0.0f;
        float bgAlpha = 0.7f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(right, bottom, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(left, bottom, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(left, top, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(right, top, 0.0D)
                .color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferUploader.drawWithShader(bufferbuilder.end());
        RenderSystem.disableBlend();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
