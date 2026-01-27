package rogo.sketch.core.driver;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public class OpenGLAPI extends GraphicsAPI {
    @Override
    public void bindTexture(int textureType, int handle) {
        GL13.glBindTexture(textureType, handle);
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

    @Override
    public void enablePolygonOffset() {
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    @Override
    public void disablePolygonOffset() {
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    @Override
    public void polygonOffset(float factor, float units) {
        GL11.glPolygonOffset(factor, units);
    }

    @Override
    public void enableLogicOp() {
        GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
    }

    @Override
    public void disableLogicOp() {
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
    }

    @Override
    public void logicOp(int opcode) {
        GL11.glLogicOp(opcode);
    }

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }

    @Override
    public int genTextures() {
        return GL11.glGenTextures();
    }

    @Override
    public void deleteTextures(int handle) {
        GL11.glDeleteTextures(handle);
    }

    @Override
    public void texParameteri(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer data) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data);
    }

    @Override
    public void generateMipmap(int target) {
        GL30.glGenerateMipmap(target);
    }

    @Override
    public void bindFrameBuffer(int target) {
        bindFrameBuffer(GL30.GL_FRAMEBUFFER, target);
    }

    @Override
    public void bindFrameBuffer(int type, int target) {
        GL30.glBindFramebuffer(type, target);
    }

    @Override
    public void bindVertexArray(int target) {
        GL30.glBindVertexArray(target);
    }
}