package rogo.sketch.render.driver;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class OpenGlAPI extends GraphicsAPI {
    @Override
    public void bindTexture(int handle) {
        GL13.glBindTexture(GL11.GL_TEXTURE_2D, handle);
    }

    @Override
    public void activeTexture(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
    }

    @Override
    public void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    @Override
    public void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void blendFunc(int src, int dst) {
        GL11.glBlendFunc(src, dst);
    }

    @Override
    public void depthMask(boolean enable) {
        GL11.glDepthMask(enable);
    }

    @Override
    public void enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    public void disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    @Override
    public void depthFunc(int func) {
        GL11.glDepthFunc(func);
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
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Override
    public void enableCullFace() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    @Override
    public void enableScissor(int x, int y, int w, int h) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, w, h);
    }

    @Override
    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
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
        GL11.glStencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int fail, int zfail, int zpass) {
        GL11.glStencilOp(fail, zfail, zpass);
    }

    @Override
    public void polygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }

    @Override
    public void viewport(int x, int y, int w, int h) {
        GL11.glViewport(x, y, w, h);
    }
}