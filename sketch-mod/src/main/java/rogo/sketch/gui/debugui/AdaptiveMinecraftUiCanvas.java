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
import rogo.sketch.core.ui.layout.UiInsets;
import rogo.sketch.core.ui.texture.UiTextureKind;
import rogo.sketch.core.ui.texture.UiTexturePatch;
import rogo.sketch.core.ui.texture.UiTextureRef;
import rogo.sketch.core.ui.texture.UiTextureRegion;
import rogo.sketch.core.ui.texture.UiTextureScaleMode;
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
    private final List<TextureQuad> textureBatch = new ArrayList<>();
    private UiScaleContext scaleContext = UiScaleContext.of(1.0f, 1, 1);
    private TextureBatchKey activeTextureKey;
    private UiTextureRef activeTexture;
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
        this.textureBatch.clear();
        this.activeTextureKey = null;
        this.activeTexture = null;
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
        textureBatch.clear();
        activeTextureKey = null;
        activeTexture = null;
    }

    @Override
    public void fillRect(UiRect rect, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        flushTextureBatch();
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
        flushTextureBatch();
        textBatch.add(new TextDraw(text, x, y, color));
    }

    @Override
    public void drawCenteredText(Component text, int centerX, int y, int color) {
        if (text == null) {
            return;
        }
        flushTextureBatch();
        int drawX = centerX - minecraft.font.width(text) / 2;
        textBatch.add(new TextDraw(text, drawX, y, color));
    }

    @Override
    public void drawTexture(UiTextureRef texture, UiRect dst, UiTextureUv uv, int tintArgb) {
        if (texture == null || !texture.isRenderable() || dst == null || dst.width() <= 0 || dst.height() <= 0) {
            return;
        }
        queueTextureQuad(texture, dst, uv != null ? uv : UiTextureUv.FULL, tintArgb);
    }

    @Override
    public void drawTexturePatch(UiTexturePatch patch, UiRect dst) {
        if (patch == null || !patch.isRenderable() || dst == null || dst.width() <= 0 || dst.height() <= 0) {
            return;
        }
        if (patch.scaleMode() == UiTextureScaleMode.NINE_SLICE && patch.sliceInsets() != UiInsets.NONE) {
            drawNineSlicePatch(patch, dst);
        } else {
            UiTextureRegion region = patch.region();
            queueTextureQuad(region.texture(), dst, region.uv(), patch.tintArgb());
        }
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
        if (!textureBatch.isEmpty()) {
            flushTextureBatch();
        }
        if (!textBatch.isEmpty()) {
            flushTextBatch();
        }
    }

    private void queueTextureQuad(UiTextureRef texture, UiRect dst, UiTextureUv uv, int tintArgb) {
        TextureBatchKey key = TextureBatchKey.from(texture);
        if (!textureBatch.isEmpty() && !key.equals(activeTextureKey)) {
            flushTextureBatch();
        }
        if (textureBatch.isEmpty()) {
            if (!geometryBatch.isEmpty()) {
                flushGeometryBatch();
            }
            if (!textBatch.isEmpty()) {
                flushTextBatch();
            }
            activeTextureKey = key;
            activeTexture = texture;
        }
        textureBatch.add(new TextureQuad(dst, uv != null ? uv : UiTextureUv.FULL, tintArgb));
    }

    private void drawNineSlicePatch(UiTexturePatch patch, UiRect dst) {
        UiTextureRegion region = patch.region();
        UiInsets sourceInsets = clampInsets(patch.sliceInsets(), region.sourceWidth(), region.sourceHeight());
        int left = Math.min(sourceInsets.left(), Math.max(0, dst.width() / 2));
        int right = Math.min(sourceInsets.right(), Math.max(0, dst.width() - left));
        int top = Math.min(sourceInsets.top(), Math.max(0, dst.height() / 2));
        int bottom = Math.min(sourceInsets.bottom(), Math.max(0, dst.height() - top));

        int[] dstX = {dst.x(), dst.x() + left, dst.right() - right, dst.right()};
        int[] dstY = {dst.y(), dst.y() + top, dst.bottom() - bottom, dst.bottom()};
        int[] srcX = {0, sourceInsets.left(), region.sourceWidth() - sourceInsets.right(), region.sourceWidth()};
        int[] srcY = {0, sourceInsets.top(), region.sourceHeight() - sourceInsets.bottom(), region.sourceHeight()};

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int width = dstX[x + 1] - dstX[x];
                int height = dstY[y + 1] - dstY[y];
                int sourceWidth = srcX[x + 1] - srcX[x];
                int sourceHeight = srcY[y + 1] - srcY[y];
                if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
                    continue;
                }
                queueTextureQuad(region.texture(),
                        new UiRect(dstX[x], dstY[y], width, height),
                        subUv(region, srcX[x], srcY[y], srcX[x + 1], srcY[y + 1]),
                        patch.tintArgb());
            }
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

    private void flushTextureBatch() {
        if (textureBatch.isEmpty()) {
            return;
        }
        if (activeTexture == null || !bindTexture(activeTexture)) {
            textureBatch.clear();
            activeTextureKey = null;
            activeTexture = null;
            return;
        }
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (TextureQuad quad : textureBatch) {
            emitTextureQuad(builder, pose, quad.rect(), quad.uv(), quad.tintArgb());
        }
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        textureBatch.clear();
        activeTextureKey = null;
        activeTexture = null;
    }

    private void flushTextBatch() {
        if (textBatch.isEmpty()) {
            return;
        }
        guiGraphics.drawManaged(() -> {
            for (TextDraw textDraw : textBatch) {
                guiGraphics.drawString(minecraft.font, textDraw.text(), textDraw.x(), textDraw.y(), textDraw.color(), false);
            }
        });
        textBatch.clear();
        //guiGraphics.flush();
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

    private void emitTextureQuad(BufferBuilder builder, Matrix4f pose, UiRect rect, UiTextureUv uv, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        builder.vertex(pose, rect.x(), rect.bottom(), 0.0F).uv(uv.u0(), uv.v1()).color(red, green, blue, alpha).endVertex();
        builder.vertex(pose, rect.right(), rect.bottom(), 0.0F).uv(uv.u1(), uv.v1()).color(red, green, blue, alpha).endVertex();
        builder.vertex(pose, rect.right(), rect.y(), 0.0F).uv(uv.u1(), uv.v0()).color(red, green, blue, alpha).endVertex();
        builder.vertex(pose, rect.x(), rect.y(), 0.0F).uv(uv.u0(), uv.v0()).color(red, green, blue, alpha).endVertex();
    }

    private UiTextureUv subUv(UiTextureRegion region, int x0, int y0, int x1, int y1) {
        float u0 = lerp(region.uv().u0(), region.uv().u1(), x0 / (float) region.sourceWidth());
        float u1 = lerp(region.uv().u0(), region.uv().u1(), x1 / (float) region.sourceWidth());
        float v0 = lerp(region.uv().v0(), region.uv().v1(), y0 / (float) region.sourceHeight());
        float v1 = lerp(region.uv().v0(), region.uv().v1(), y1 / (float) region.sourceHeight());
        return new UiTextureUv(u0, v0, u1, v1);
    }

    private float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    private UiInsets clampInsets(UiInsets insets, int width, int height) {
        int left = Math.min(insets.left(), Math.max(0, width / 2));
        int right = Math.min(insets.right(), Math.max(0, width - left));
        int top = Math.min(insets.top(), Math.max(0, height / 2));
        int bottom = Math.min(insets.bottom(), Math.max(0, height - top));
        return new UiInsets(left, top, right, bottom);
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

    private record TextureQuad(UiRect rect, UiTextureUv uv, int tintArgb) {
    }

    private record TextureBatchKey(UiTextureKind kind, String resourceId, long handleValue) {
        private static TextureBatchKey from(UiTextureRef texture) {
            return new TextureBatchKey(
                    texture.kind(),
                    texture.kind() == UiTextureKind.MINECRAFT_RESOURCE ? texture.resourceId() : "",
                    texture.kind() == UiTextureKind.GPU_HANDLE ? texture.handle().value() : 0L);
        }
    }

}
