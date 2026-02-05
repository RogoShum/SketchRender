package rogo.sketch.core.driver;

import org.lwjgl.opengl.*;
import rogo.sketch.core.driver.internal.*;
import rogo.sketch.core.state.snapshot.GLStateSnapshot;
import rogo.sketch.core.state.snapshot.SnapshotScope;
import rogo.sketch.core.util.GLFeatureChecker;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OpenGL implementation of GraphicsAPI.
 * Automatically selects DSA or Legacy strategies based on GL capabilities.
 */
public class OpenGLAPI extends GraphicsAPI {

    // Strategies
    private final IGLBufferStrategy bufferStrategy;
    private final IGLTextureStrategy textureStrategy;
    private final IGLShaderStrategy shaderStrategy;
    private final IGLFramebufferStrategy framebufferStrategy;
    private final IGLVertexArrayStrategy vertexArrayStrategy;

    private final boolean useDSA;

    public OpenGLAPI() {
        // Initialize GLFeatureChecker if not already done
        try {
            GLFeatureChecker.initialize();
        } catch (IllegalStateException e) {
            // Already initialized or GL context not ready
        }

        // Determine if we should use DSA
        this.useDSA = GLFeatureChecker.supportsDSA();

        // Initialize strategies based on capabilities
        if (useDSA) {
            this.bufferStrategy = new DSABufferStrategy();
            this.textureStrategy = new DSATextureStrategy();
            this.shaderStrategy = new DSAShaderStrategy();
            this.framebufferStrategy = new DSAFramebufferStrategy();
            this.vertexArrayStrategy = new DSAVertexArrayStrategy();
        } else {
            this.bufferStrategy = new LegacyBufferStrategy();
            this.textureStrategy = new LegacyTextureStrategy();
            this.shaderStrategy = new LegacyShaderStrategy();
            this.framebufferStrategy = new LegacyFramebufferStrategy();
            this.vertexArrayStrategy = new LegacyVertexArrayStrategy();
        }
    }

    // ==================== Strategy Accessors ====================

    @Override
    public IGLBufferStrategy getBufferStrategy() {
        return bufferStrategy;
    }

    @Override
    public IGLTextureStrategy getTextureStrategy() {
        return textureStrategy;
    }

    @Override
    public IGLShaderStrategy getShaderStrategy() {
        return shaderStrategy;
    }

    @Override
    public IGLFramebufferStrategy getFramebufferStrategy() {
        return framebufferStrategy;
    }

    @Override
    public IGLVertexArrayStrategy getVertexArrayStrategy() {
        return vertexArrayStrategy;
    }

    // ==================== Blend State ====================

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
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquation(int mode) {
        GL14.glBlendEquation(mode);
    }

    @Override
    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    // ==================== Depth State ====================

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
    public void depthMask(boolean enable) {
        GL11.glDepthMask(enable);
    }

    @Override
    public void depthRange(double near, double far) {
        GL11.glDepthRange(near, far);
    }

    // ==================== Cull State ====================

    @Override
    public void enableCullFace() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    @Override
    public void disableCullFace() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Override
    public void cullFace(int face) {
        GL11.glCullFace(face);
    }

    @Override
    public void frontFace(int face) {
        GL11.glFrontFace(face);
    }

    // ==================== Scissor State ====================

    @Override
    public void enableScissor(int x, int y, int w, int h) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, w, h);
    }

    @Override
    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ==================== Stencil State ====================

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
    public void stencilMask(int mask) {
        GL11.glStencilMask(mask);
    }

    // ==================== Polygon State ====================

    @Override
    public void polygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
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

    // ==================== Viewport ====================

    @Override
    public void viewport(int x, int y, int w, int h) {
        GL11.glViewport(x, y, w, h);
    }

    // ==================== Color Mask ====================

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }

    // ==================== Logic Op ====================

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

    // ==================== Clear Operations ====================

    @Override
    public void clear(int mask) {
        GL11.glClear(mask);
    }

    @Override
    public void clearColor(float r, float g, float b, float a) {
        GL11.glClearColor(r, g, b, a);
    }

    @Override
    public void clearDepth(double depth) {
        GL11.glClearDepth(depth);
    }

    @Override
    public void clearStencil(int stencil) {
        GL11.glClearStencil(stencil);
    }

    // ==================== Legacy Texture Methods ====================

    @Override
    public void bindTexture(int target, int id) {
        textureStrategy.bindTexture(target, id);
    }

    @Override
    public void activeTexture(int unit) {
        textureStrategy.activeTexture(unit);
    }

    @Override
    @Deprecated
    public void texImage2D(int target, int level, int internalFormat, int width, int height,
                           int border, int format, int type, ByteBuffer data) {
        // Legacy method - use direct GL call with target
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data);
    }

    @Override
    @Deprecated
    public void texParameteri(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }

    @Override
    @Deprecated
    public void generateMipmap(int target) {
        GL30.glGenerateMipmap(target);
    }

    // ==================== Compute Shader Operations ====================

    @Override
    public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    @Override
    public void memoryBarrier(int barriers) {
        GL42.glMemoryBarrier(barriers);
    }

    // ==================== Draw Operations ====================

    @Override
    public void drawArrays(int mode, int first, int count) {
        GL11.glDrawArrays(mode, first, count);
    }

    @Override
    public void drawElements(int mode, int count, int type, long indices) {
        GL11.glDrawElements(mode, count, type, indices);
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, long indices, int instanceCount) {
        GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }

    @Override
    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawCount, int stride) {
        GL43.glMultiDrawElementsIndirect(mode, type, indirect, drawCount, stride);
    }

    // ==================== State Snapshot Operations ====================

    @Override
    public GLStateSnapshot snapshot(SnapshotScope scope) {
        GLStateSnapshot snapshot = new GLStateSnapshot(scope);
        snapshot.capture();
        return snapshot;
    }

    @Override
    public void restore(GLStateSnapshot snapshot) {
        snapshot.restore();
    }

    // ==================== Query Operations ====================

    @Override
    public int getInteger(int pname) {
        return GL11.glGetInteger(pname);
    }

    @Override
    public boolean getBoolean(int pname) {
        return GL11.glGetBoolean(pname);
    }

    @Override
    public float getFloat(int pname) {
        return GL11.glGetFloat(pname);
    }

    @Override
    public boolean isEnabled(int cap) {
        return GL11.glIsEnabled(cap);
    }

    // ==================== Utility Methods ====================

    @Override
    public boolean usesDSA() {
        return useDSA;
    }

    @Override
    public String getAPIName() {
        return useDSA ? "OpenGL (DSA)" : "OpenGL (Legacy)";
    }

    @Override
    public void flush() {
        GL11.glFlush();
    }

    @Override
    public void finish() {
        GL11.glFinish();
    }
}
