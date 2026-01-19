package rogo.sketch.render.driver;

public abstract class GraphicsAPI {
    public abstract void bindTexture(int handle);

    public abstract void activeTexture(int unit);

    public abstract void enableBlend();

    public abstract void disableBlend();

    public abstract void blendFunc(int src, int dst);

    public abstract void depthMask(boolean enable);

    public abstract void enableDepthTest();

    public abstract void disableDepthTest();

    public abstract void depthFunc(int func);

    public abstract void cullFace(int face);

    public abstract void frontFace(int face);

    public abstract void disableCullFace();

    public abstract void enableCullFace();

    public abstract void enableScissor(int x, int y, int w, int h);

    public abstract void disableScissor();

    public abstract void enableStencil();

    public abstract void disableStencil();

    public abstract void stencilFunc(int func, int ref, int mask);

    public abstract void stencilOp(int fail, int zfail, int zpass);

    public abstract void polygonMode(int face, int mode);

    public abstract void viewport(int x, int y, int w, int h);

    public abstract void enablePolygonOffset();

    public abstract void disablePolygonOffset();

    public abstract void polygonOffset(float factor, float units);

    public abstract void enableLogicOp();

    public abstract void disableLogicOp();

    public abstract void logicOp(int opcode);

    public abstract void colorMask(boolean red, boolean green, boolean blue, boolean alpha);
}