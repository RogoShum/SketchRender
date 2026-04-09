package rogo.sketch.backend.opengl;

/**
 * Generic OpenGL state access surface used by the backend runtime and external
 * host wrappers.
 * <p>
 * The backend only depends on this neutral contract. Host integrations may
 * implement it to keep their CPU-side state tracking coherent.
 */
public interface OpenGLStateAccess {
    void enableBlend();

    void disableBlend();

    void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);

    void blendEquationSeparate(int modeRGB, int modeAlpha);

    void enableDepthTest();

    void disableDepthTest();

    void depthFunc(int func);

    void depthMask(boolean enable);

    void enableCullFace();

    void disableCullFace();

    void cullFace(int face);

    void frontFace(int face);

    void enableScissor(int x, int y, int width, int height);

    void disableScissor();

    void enableStencil();

    void disableStencil();

    void stencilFunc(int func, int ref, int mask);

    void stencilOp(int fail, int zfail, int zpass);

    void enablePolygonOffset();

    void disablePolygonOffset();

    void polygonOffset(float factor, float units);

    void enableLogicOp();

    void disableLogicOp();

    void logicOp(int opcode);

    void colorMask(boolean red, boolean green, boolean blue, boolean alpha);

    void viewport(int x, int y, int width, int height);

    void bindFramebuffer(int target, int framebuffer);

    void bindVertexArray(int vao);
}
