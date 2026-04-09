package rogo.sketch.vanilla.backend.opengl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import rogo.sketch.backend.opengl.OpenGLStateAccess;

public final class LegacyMinecraftOpenGLStateAccess implements OpenGLStateAccess {
    @Override
    public void enableBlend() {
        RenderSystem.enableBlend();
    }

    @Override
    public void disableBlend() {
        RenderSystem.disableBlend();
    }

    @Override
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        if (modeRGB == modeAlpha) {
            RenderSystem.blendEquation(modeRGB);
            return;
        }
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @Override
    public void enableDepthTest() {
        RenderSystem.enableDepthTest();
    }

    @Override
    public void disableDepthTest() {
        RenderSystem.disableDepthTest();
    }

    @Override
    public void depthFunc(int func) {
        RenderSystem.depthFunc(func);
    }

    @Override
    public void depthMask(boolean enable) {
        RenderSystem.depthMask(enable);
    }

    @Override
    public void enableCullFace() {
        RenderSystem.enableCull();
    }

    @Override
    public void disableCullFace() {
        RenderSystem.disableCull();
    }

    @Override
    public void cullFace(int face) {
        GL11.glCullFace(face);
    }

    @Override
    public void frontFace(int face) {
        GL11.glFrontFace(face);
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        RenderSystem.enableScissor(x, y, width, height);
    }

    @Override
    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    @Override
    public void enableStencil() {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
    }

    @Override
    public void disableStencil() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        RenderSystem.stencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int fail, int zfail, int zpass) {
        RenderSystem.stencilOp(fail, zfail, zpass);
    }

    @Override
    public void enablePolygonOffset() {
        RenderSystem.enablePolygonOffset();
    }

    @Override
    public void disablePolygonOffset() {
        RenderSystem.disablePolygonOffset();
    }

    @Override
    public void polygonOffset(float factor, float units) {
        RenderSystem.polygonOffset(factor, units);
    }

    @Override
    public void enableLogicOp() {
        RenderSystem.enableColorLogicOp();
    }

    @Override
    public void disableLogicOp() {
        RenderSystem.disableColorLogicOp();
    }

    @Override
    public void logicOp(int opcode) {
        GlStateManager._logicOp(opcode);
    }

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        RenderSystem.colorMask(red, green, blue, alpha);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        RenderSystem.viewport(x, y, width, height);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GlStateManager._glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void bindVertexArray(int vao) {
        GlStateManager._glBindVertexArray(vao);
    }
}
