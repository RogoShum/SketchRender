package rogo.sketch.vanilla.backend.opengl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import rogo.sketch.backend.opengl.NativeOpenGLStateAccess;
import rogo.sketch.backend.opengl.OpenGLStateAccess;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;

public final class MinecraftOpenGLStateAccess implements OpenGLStateAccess {
    private final OpenGLStateAccess nativeAccess;

    public MinecraftOpenGLStateAccess(GraphicsAPI api) {
        this.nativeAccess = new NativeOpenGLStateAccess(api);
    }

    private boolean useRenderSystemAccess() {
        return RenderSystem.isOnRenderThread();
    }

    @Override
    public void enableBlend() {
        if (useRenderSystemAccess()) {
            RenderSystem.enableBlend();
            return;
        }
        nativeAccess.enableBlend();
    }

    @Override
    public void disableBlend() {
        if (useRenderSystemAccess()) {
            RenderSystem.disableBlend();
            return;
        }
        nativeAccess.disableBlend();
    }

    @Override
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (useRenderSystemAccess()) {
            RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            return;
        }
        nativeAccess.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        if (!useRenderSystemAccess()) {
            nativeAccess.blendEquationSeparate(modeRGB, modeAlpha);
            return;
        }
        if (modeRGB == modeAlpha) {
            RenderSystem.blendEquation(modeRGB);
            return;
        }
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @Override
    public void enableDepthTest() {
        if (useRenderSystemAccess()) {
            RenderSystem.enableDepthTest();
            return;
        }
        nativeAccess.enableDepthTest();
    }

    @Override
    public void disableDepthTest() {
        if (useRenderSystemAccess()) {
            RenderSystem.disableDepthTest();
            return;
        }
        nativeAccess.disableDepthTest();
    }

    @Override
    public void depthFunc(int func) {
        if (useRenderSystemAccess()) {
            RenderSystem.depthFunc(func);
            return;
        }
        nativeAccess.depthFunc(func);
    }

    @Override
    public void depthMask(boolean enable) {
        if (useRenderSystemAccess()) {
            RenderSystem.depthMask(enable);
            return;
        }
        nativeAccess.depthMask(enable);
    }

    @Override
    public void enableCullFace() {
        if (useRenderSystemAccess()) {
            RenderSystem.enableCull();
            return;
        }
        nativeAccess.enableCullFace();
    }

    @Override
    public void disableCullFace() {
        if (useRenderSystemAccess()) {
            RenderSystem.disableCull();
            return;
        }
        nativeAccess.disableCullFace();
    }

    @Override
    public void cullFace(int face) {
        if (useRenderSystemAccess()) {
            GL11.glCullFace(face);
            return;
        }
        nativeAccess.cullFace(face);
    }

    @Override
    public void frontFace(int face) {
        if (useRenderSystemAccess()) {
            GL11.glFrontFace(face);
            return;
        }
        nativeAccess.frontFace(face);
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        if (useRenderSystemAccess()) {
            RenderSystem.enableScissor(x, y, width, height);
            return;
        }
        nativeAccess.enableScissor(x, y, width, height);
    }

    @Override
    public void disableScissor() {
        if (useRenderSystemAccess()) {
            RenderSystem.disableScissor();
            return;
        }
        nativeAccess.disableScissor();
    }

    @Override
    public void enableStencil() {
        if (useRenderSystemAccess()) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            return;
        }
        nativeAccess.enableStencil();
    }

    @Override
    public void disableStencil() {
        if (useRenderSystemAccess()) {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            return;
        }
        nativeAccess.disableStencil();
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        if (useRenderSystemAccess()) {
            RenderSystem.stencilFunc(func, ref, mask);
            return;
        }
        nativeAccess.stencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int fail, int zfail, int zpass) {
        if (useRenderSystemAccess()) {
            RenderSystem.stencilOp(fail, zfail, zpass);
            return;
        }
        nativeAccess.stencilOp(fail, zfail, zpass);
    }

    @Override
    public void enablePolygonOffset() {
        if (useRenderSystemAccess()) {
            RenderSystem.enablePolygonOffset();
            return;
        }
        nativeAccess.enablePolygonOffset();
    }

    @Override
    public void disablePolygonOffset() {
        if (useRenderSystemAccess()) {
            RenderSystem.disablePolygonOffset();
            return;
        }
        nativeAccess.disablePolygonOffset();
    }

    @Override
    public void polygonOffset(float factor, float units) {
        if (useRenderSystemAccess()) {
            RenderSystem.polygonOffset(factor, units);
            return;
        }
        nativeAccess.polygonOffset(factor, units);
    }

    @Override
    public void enableLogicOp() {
        if (useRenderSystemAccess()) {
            RenderSystem.enableColorLogicOp();
            return;
        }
        nativeAccess.enableLogicOp();
    }

    @Override
    public void disableLogicOp() {
        if (useRenderSystemAccess()) {
            RenderSystem.disableColorLogicOp();
            return;
        }
        nativeAccess.disableLogicOp();
    }

    @Override
    public void logicOp(int opcode) {
        if (useRenderSystemAccess()) {
            GlStateManager._logicOp(opcode);
            return;
        }
        nativeAccess.logicOp(opcode);
    }

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (useRenderSystemAccess()) {
            RenderSystem.colorMask(red, green, blue, alpha);
            return;
        }
        nativeAccess.colorMask(red, green, blue, alpha);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        if (useRenderSystemAccess()) {
            RenderSystem.viewport(x, y, width, height);
            return;
        }
        nativeAccess.viewport(x, y, width, height);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        if (useRenderSystemAccess()) {
            GlStateManager._glBindFramebuffer(target, framebuffer);
            return;
        }
        nativeAccess.bindFramebuffer(target, framebuffer);
    }

    @Override
    public void bindVertexArray(int vao) {
        if (useRenderSystemAccess()) {
            GlStateManager._glBindVertexArray(vao);
            return;
        }
        nativeAccess.bindVertexArray(vao);
    }
}
