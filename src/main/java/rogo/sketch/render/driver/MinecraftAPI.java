package rogo.sketch.render.driver;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

public class MinecraftAPI extends GraphicsAPI {

    @Override
    public void bindTexture(int handle) {
        RenderSystem.bindTexture(handle);
    }

    @Override
    public void activeTexture(int unit) {
        RenderSystem.activeTexture(unit);
    }

    @Override
    public void enableBlend() {
        RenderSystem.enableBlend();
    }

    @Override
    public void disableBlend() {
        RenderSystem.disableBlend();
    }

    @Override
    public void blendFunc(int src, int dst) {
        RenderSystem.blendFunc(src, dst);
    }

    @Override
    public void depthMask(boolean enable) {
        RenderSystem.depthMask(enable);
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
    public void cullFace(int face) {
        GL11.glCullFace(face);
    }

    @Override
    public void frontFace(int face) {
        GL11.glFrontFace(face);
    }

    @Override
    public void disableCullFace() {
        RenderSystem.disableCull();
    }

    @Override
    public void enableCullFace() {
        RenderSystem.enableCull();
    }

    @Override
    public void enableScissor(int x, int y, int w, int h) {
        RenderSystem.enableScissor(x, y, w, h);
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
    public void polygonMode(int face, int mode) {
        RenderSystem.polygonMode(face, mode);
    }

    @Override
    public void viewport(int x, int y, int w, int h) {
        RenderSystem.viewport(x, y, w, h);
    }
}