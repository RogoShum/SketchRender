package rogo.sketch.gui.debugui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.geometry.UiScaleContext;
import org.joml.Matrix4f;
import rogo.sketch.core.ui.texture.UiTextureKind;
import rogo.sketch.core.ui.texture.UiTextureRef;
import rogo.sketch.core.ui.texture.UiTextureUv;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class AdaptiveMinecraftUiCanvas implements UiCanvas {
    private final GuiGraphics guiGraphics;
    private final Minecraft minecraft;
    private final Deque<UiRect> clipStack = new ArrayDeque<>();
    private final List<GeometryQuad> geometryBatch = new ArrayList<>();
    private final List<TextDraw> textBatch = new ArrayList<>();
    private UiScaleContext scaleContext = UiScaleContext.of(1.0f, 1, 1);
    private boolean frameActive;

    public AdaptiveMinecraftUiCanvas(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    public void beginFrame(UiScaleContext scaleContext) {
        if (frameActive) {
            endFrame();
        }
        this.scaleContext = scaleContext != null ? scaleContext : UiScaleContext.of(1.0f, 1, 1);
        this.geometryBatch.clear();
        this.textBatch.clear();
        this.clipStack.clear();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(this.scaleContext.rootScale(), this.scaleContext.rootScale(), 1.0f);
        frameActive = true;
    }

    @Override
    public void endFrame() {
        flush();
        while (!clipStack.isEmpty()) {
            clipStack.pop();
            guiGraphics.disableScissor();
        }
        if (frameActive) {
            guiGraphics.pose().popPose();
            frameActive = false;
        }
        geometryBatch.clear();
        textBatch.clear();
    }

    @Override
    public void fillRect(UiRect rect, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        geometryBatch.add(new GeometryQuad(rect, color));
    }

    @Override
    public void borderRect(UiRect rect, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        fillRect(new UiRect(rect.x(), rect.y(), rect.width(), 1), color);
        fillRect(new UiRect(rect.x(), rect.bottom() - 1, rect.width(), 1), color);
        fillRect(new UiRect(rect.x(), rect.y(), 1, rect.height()), color);
        fillRect(new UiRect(rect.right() - 1, rect.y(), 1, rect.height()), color);
    }

    @Override
    public void drawText(Component text, int x, int y, int color) {
        if (text == null) {
            return;
        }
        textBatch.add(new TextDraw(text, x, y, color));
    }

    @Override
    public void drawCenteredText(Component text, int centerX, int y, int color) {
        if (text == null) {
            return;
        }
        int drawX = centerX - minecraft.font.width(text) / 2;
        textBatch.add(new TextDraw(text, drawX, y, color));
    }

    @Override
    public void drawTexture(UiTextureRef texture, UiRect dst, UiTextureUv uv, int tintArgb) {
        if (texture == null || !texture.isRenderable() || dst == null || dst.width() <= 0 || dst.height() <= 0) {
            return;
        }
        flush();
        if (!bindTexture(texture)) {
            return;
        }
        UiTextureUv resolvedUv = uv != null ? uv : UiTextureUv.FULL;
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        float alpha = ((tintArgb >>> 24) & 0xFF) / 255.0F;
        float red = ((tintArgb >>> 16) & 0xFF) / 255.0F;
        float green = ((tintArgb >>> 8) & 0xFF) / 255.0F;
        float blue = (tintArgb & 0xFF) / 255.0F;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(pose, dst.x(), dst.bottom(), 0.0F).uv(resolvedUv.u0(), resolvedUv.v1()).color(red, green, blue, alpha).endVertex();
        builder.vertex(pose, dst.right(), dst.bottom(), 0.0F).uv(resolvedUv.u1(), resolvedUv.v1()).color(red, green, blue, alpha).endVertex();
        builder.vertex(pose, dst.right(), dst.y(), 0.0F).uv(resolvedUv.u1(), resolvedUv.v0()).color(red, green, blue, alpha).endVertex();
        builder.vertex(pose, dst.x(), dst.y(), 0.0F).uv(resolvedUv.u0(), resolvedUv.v0()).color(red, green, blue, alpha).endVertex();
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    @Override
    public int width(Component text) {
        return minecraft.font.width(text);
    }

    @Override
    public int lineHeight() {
        return minecraft.font.lineHeight;
    }

    @Override
    public void pushClip(UiRect rect) {
        flush();
        UiRect clipped = clipStack.isEmpty() ? rect : intersect(clipStack.peek(), rect);
        clipStack.push(clipped);
        UiRect physical = scaleContext.toPhysicalRect(clipped);
        guiGraphics.enableScissor(physical.x(), physical.y(), physical.right(), physical.bottom());
    }

    @Override
    public void popClip() {
        if (clipStack.isEmpty()) {
            return;
        }
        flush();
        clipStack.pop();
        guiGraphics.disableScissor();
    }

    @Override
    public void flush() {
        if (!geometryBatch.isEmpty()) {
            flushGeometryBatch();
        }
        if (!textBatch.isEmpty()) {
            flushTextBatch();
        }
    }

    private void flushGeometryBatch() {
        if (geometryBatch.isEmpty()) {
            return;
        }
        Matrix4f pose = guiGraphics.pose().last().pose();
        VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        for (GeometryQuad quad : geometryBatch) {
            emitQuad(vertexConsumer, pose, quad.rect(), quad.color());
        }
        geometryBatch.clear();
        guiGraphics.flush();
    }

    private void flushTextBatch() {
        if (textBatch.isEmpty()) {
            return;
        }
        for (TextDraw textDraw : textBatch) {
            guiGraphics.drawString(minecraft.font, textDraw.text(), textDraw.x(), textDraw.y(), textDraw.color(), false);
        }
        textBatch.clear();
        guiGraphics.flush();
    }

    private UiRect intersect(UiRect a, UiRect b) {
        int x = Math.max(a.x(), b.x());
        int y = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        return new UiRect(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private void emitQuad(VertexConsumer vertexConsumer, Matrix4f pose, UiRect rect, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        vertexConsumer.vertex(pose, rect.x(), rect.bottom(), 0.0F).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(pose, rect.right(), rect.bottom(), 0.0F).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(pose, rect.right(), rect.y(), 0.0F).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(pose, rect.x(), rect.y(), 0.0F).color(red, green, blue, alpha).endVertex();
    }

    private boolean bindTexture(UiTextureRef texture) {
        if (texture.kind() == UiTextureKind.GPU_HANDLE) {
            try {
                RenderSystem.setShaderTexture(0, texture.handle().asGlName());
                return true;
            } catch (ArithmeticException ignored) {
                return false;
            }
        }
        String resourceId = texture.resourceId();
        if (resourceId == null || resourceId.isBlank()) {
            return false;
        }
        try {
            RenderSystem.setShaderTexture(0, new ResourceLocation(resourceId));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record GeometryQuad(UiRect rect, int color) {
    }

    private record TextDraw(Component text, int x, int y, int color) {
    }

}
