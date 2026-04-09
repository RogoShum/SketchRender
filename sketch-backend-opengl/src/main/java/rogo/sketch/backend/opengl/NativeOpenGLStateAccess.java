package rogo.sketch.backend.opengl;

import rogo.sketch.backend.opengl.driver.GraphicsAPI;

final class NativeOpenGLStateAccess implements OpenGLStateAccess {
    private final GraphicsAPI api;

    NativeOpenGLStateAccess(GraphicsAPI api) {
        this.api = api;
    }

    @Override
    public void enableBlend() {
        api.enableBlend();
    }

    @Override
    public void disableBlend() {
        api.disableBlend();
    }

    @Override
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        api.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        api.blendEquationSeparate(modeRGB, modeAlpha);
    }

    @Override
    public void enableDepthTest() {
        api.enableDepthTest();
    }

    @Override
    public void disableDepthTest() {
        api.disableDepthTest();
    }

    @Override
    public void depthFunc(int func) {
        api.depthFunc(func);
    }

    @Override
    public void depthMask(boolean enable) {
        api.depthMask(enable);
    }

    @Override
    public void enableCullFace() {
        api.enableCullFace();
    }

    @Override
    public void disableCullFace() {
        api.disableCullFace();
    }

    @Override
    public void cullFace(int face) {
        api.cullFace(face);
    }

    @Override
    public void frontFace(int face) {
        api.frontFace(face);
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        api.enableScissor(x, y, width, height);
    }

    @Override
    public void disableScissor() {
        api.disableScissor();
    }

    @Override
    public void enableStencil() {
        api.enableStencil();
    }

    @Override
    public void disableStencil() {
        api.disableStencil();
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        api.stencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int fail, int zfail, int zpass) {
        api.stencilOp(fail, zfail, zpass);
    }

    @Override
    public void enablePolygonOffset() {
        api.enablePolygonOffset();
    }

    @Override
    public void disablePolygonOffset() {
        api.disablePolygonOffset();
    }

    @Override
    public void polygonOffset(float factor, float units) {
        api.polygonOffset(factor, units);
    }

    @Override
    public void enableLogicOp() {
        api.enableLogicOp();
    }

    @Override
    public void disableLogicOp() {
        api.disableLogicOp();
    }

    @Override
    public void logicOp(int opcode) {
        api.logicOp(opcode);
    }

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        api.colorMask(red, green, blue, alpha);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        api.viewport(x, y, width, height);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        api.bindFrameBuffer(target, framebuffer);
    }

    @Override
    public void bindVertexArray(int vao) {
        api.bindVertexArray(vao);
    }
}
