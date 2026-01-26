package rogo.sketch.vanilla.driver;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL13;
import rogo.sketch.core.driver.OpenGLAPI;

public class MinecraftAPI extends OpenGLAPI {

    @Override
    public void bindTexture(int textureType, int handle) {
        RenderSystem.bindTexture(handle);
    }

    @Override
    public void activeTexture(int unit) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
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
}