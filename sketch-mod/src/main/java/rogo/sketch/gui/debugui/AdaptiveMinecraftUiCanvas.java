package rogo.sketch.gui.debugui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import rogo.sketch.core.ui.geometry.UiRect;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class AdaptiveMinecraftUiCanvas implements UiCanvas {
    private final GuiGraphics guiGraphics;
    private final Minecraft minecraft;
    private final Deque<UiRect> clipStack = new ArrayDeque<>();
    private final List<GeometryQuad> geometryBatch = new ArrayList<>();

    public AdaptiveMinecraftUiCanvas(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
        this.minecraft = Minecraft.getInstance();
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
        flush();
        guiGraphics.drawString(minecraft.font, text, x, y, color, false);
        guiGraphics.flush();
    }

    @Override
    public void drawCenteredText(Component text, int centerX, int y, int color) {
        flush();
        int drawX = centerX - minecraft.font.width(text) / 2;
        guiGraphics.drawString(minecraft.font, text, drawX, y, color, false);
        guiGraphics.flush();
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
        guiGraphics.enableScissor(clipped.x(), clipped.y(), clipped.right(), clipped.bottom());
    }

    @Override
    public void popClip() {
        if (clipStack.isEmpty()) {
            return;
        }
        flush();
        clipStack.pop();
        if (clipStack.isEmpty()) {
            guiGraphics.disableScissor();
        } else {
            UiRect rect = clipStack.peek();
            guiGraphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        }
    }

    @Override
    public void flush() {
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

    private record GeometryQuad(UiRect rect, int color) {
    }
}

