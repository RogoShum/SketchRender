package rogo.sketch.backend.opengl.driver;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import rogo.sketch.backend.opengl.internal.*;
import rogo.sketch.backend.opengl.state.snapshot.GLStateSnapshot;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.backend.opengl.util.GLFeatureChecker;

import java.nio.ByteBuffer;

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

    // Shared GL contexts for worker threads
    private long sharedRenderWindowHandle = 0;
    private long sharedTickWindowHandle = 0;
    private long sharedUploadWindowHandle = 0;
    private long sharedComputeWindowHandle = 0;
    private long sharedOffscreenGraphicsWindowHandle = 0;

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

    // ==================== Render Worker Context Lifecycle ====================

    @Override
    public void initRenderWorkerContext(long mainWindowHandle) {
        if (sharedRenderWindowHandle != 0) return;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        sharedRenderWindowHandle = GLFW.glfwCreateWindow(1, 1, "Sketch-RenderWorker", 0, mainWindowHandle);
        if (sharedRenderWindowHandle == 0) {
            throw new RuntimeException("[OpenGLAPI] Failed to create shared GLFW window for render worker");
        }
        setRenderWorkerReady(true);
    }

    @Override
    public void initTickWorkerContext(long mainWindowHandle) {
        if (sharedTickWindowHandle != 0) return;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        sharedTickWindowHandle = GLFW.glfwCreateWindow(1, 1, "Sketch-TickWorker", 0, mainWindowHandle);
        if (sharedTickWindowHandle == 0) {
            throw new RuntimeException("[OpenGLAPI] Failed to create shared GLFW window for tick worker");
        }
        setTickWorkerReady(true);
    }

    @Override
    public void initUploadWorkerContext(long mainWindowHandle) {
        if (sharedUploadWindowHandle != 0) return;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        sharedUploadWindowHandle = GLFW.glfwCreateWindow(1, 1, "Sketch-UploadWorker", 0, mainWindowHandle);
        if (sharedUploadWindowHandle == 0) {
            throw new RuntimeException("[OpenGLAPI] Failed to create shared GLFW window for upload worker");
        }
        setUploadWorkerReady(true);
    }

    @Override
    public void initComputeWorkerContext(long mainWindowHandle) {
        if (sharedComputeWindowHandle != 0) return;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        sharedComputeWindowHandle = GLFW.glfwCreateWindow(1, 1, "Sketch-ComputeWorker", 0, mainWindowHandle);
        if (sharedComputeWindowHandle == 0) {
            throw new RuntimeException("[OpenGLAPI] Failed to create shared GLFW window for compute worker");
        }
        setComputeWorkerReady(true);
    }

    @Override
    public void initOffscreenGraphicsWorkerContext(long mainWindowHandle) {
        if (sharedOffscreenGraphicsWindowHandle != 0) return;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        sharedOffscreenGraphicsWindowHandle = GLFW.glfwCreateWindow(1, 1, "Sketch-OffscreenGraphicsWorker", 0, mainWindowHandle);
        if (sharedOffscreenGraphicsWindowHandle == 0) {
            throw new RuntimeException("[OpenGLAPI] Failed to create shared GLFW window for offscreen graphics worker");
        }
        setOffscreenGraphicsWorkerReady(true);
    }

    @Override
    public void onRenderWorkerThreadStart() {
        if (sharedRenderWindowHandle == 0) return;
        registerRenderWorkerThread();
        GLFW.glfwMakeContextCurrent(sharedRenderWindowHandle);
        GL.createCapabilities();
    }

    @Override
    public void onTickWorkerThreadStart() {
        if (sharedTickWindowHandle == 0) return;
        registerTickWorkerThread();
        GLFW.glfwMakeContextCurrent(sharedTickWindowHandle);
        GL.createCapabilities();
    }

    @Override
    public void onUploadWorkerThreadStart() {
        if (sharedUploadWindowHandle == 0) return;
        registerUploadWorkerThread();
        GLFW.glfwMakeContextCurrent(sharedUploadWindowHandle);
        GL.createCapabilities();
    }

    @Override
    public void onComputeWorkerThreadStart() {
        if (sharedComputeWindowHandle == 0) return;
        registerComputeWorkerThread();
        GLFW.glfwMakeContextCurrent(sharedComputeWindowHandle);
        GL.createCapabilities();
    }

    @Override
    public void onOffscreenGraphicsWorkerThreadStart() {
        if (sharedOffscreenGraphicsWindowHandle == 0) return;
        registerOffscreenGraphicsWorkerThread();
        GLFW.glfwMakeContextCurrent(sharedOffscreenGraphicsWindowHandle);
        GL.createCapabilities();
    }

    @Override
    public void onRenderWorkerThreadEnd() {
        GLFW.glfwMakeContextCurrent(0);
    }

    @Override
    public void onTickWorkerThreadEnd() {
        GLFW.glfwMakeContextCurrent(0);
    }

    @Override
    public void onUploadWorkerThreadEnd() {
        GLFW.glfwMakeContextCurrent(0);
    }

    @Override
    public void onComputeWorkerThreadEnd() {
        GLFW.glfwMakeContextCurrent(0);
    }

    @Override
    public void onOffscreenGraphicsWorkerThreadEnd() {
        GLFW.glfwMakeContextCurrent(0);
    }

    @Override
    public void destroyRenderWorkerContext() {
        if (sharedRenderWindowHandle != 0) {
            GLFW.glfwDestroyWindow(sharedRenderWindowHandle);
            sharedRenderWindowHandle = 0;
            setRenderWorkerReady(false);
        }
    }

    @Override
    public void destroyTickWorkerContext() {
        if (sharedTickWindowHandle != 0) {
            GLFW.glfwDestroyWindow(sharedTickWindowHandle);
            sharedTickWindowHandle = 0;
            setTickWorkerReady(false);
        }
    }

    @Override
    public void destroyUploadWorkerContext() {
        if (sharedUploadWindowHandle != 0) {
            GLFW.glfwDestroyWindow(sharedUploadWindowHandle);
            sharedUploadWindowHandle = 0;
            setUploadWorkerReady(false);
        }
    }

    @Override
    public void destroyComputeWorkerContext() {
        if (sharedComputeWindowHandle != 0) {
            GLFW.glfwDestroyWindow(sharedComputeWindowHandle);
            sharedComputeWindowHandle = 0;
            setComputeWorkerReady(false);
        }
    }

    @Override
    public void destroyOffscreenGraphicsWorkerContext() {
        if (sharedOffscreenGraphicsWindowHandle != 0) {
            GLFW.glfwDestroyWindow(sharedOffscreenGraphicsWindowHandle);
            sharedOffscreenGraphicsWindowHandle = 0;
            setOffscreenGraphicsWorkerReady(false);
        }
    }

    // ==================== Sync Primitives ====================

    @Override
    public long createFenceSync() {
        return GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    @Override
    public boolean clientWaitSync(long fence, long timeoutNanos) {
        return clientWaitSync(fence, timeoutNanos, true);
    }

    @Override
    public boolean clientWaitSync(long fence, long timeoutNanos, boolean flushCommands) {
        int flags = flushCommands ? GL32.GL_SYNC_FLUSH_COMMANDS_BIT : 0;
        int result = GL32.glClientWaitSync(fence, flags, timeoutNanos);
        return result == GL32.GL_ALREADY_SIGNALED || result == GL32.GL_CONDITION_SATISFIED;
    }

    @Override
    public void deleteFenceSync(long fence) {
        if (fence != 0) {
            GL32.glDeleteSync(fence);
        }
    }

    @Override
    public void flushMappedBufferRange(int bufferHandle, long offset, long length) {
        getBufferStrategy().bindBuffer(GL15.GL_ARRAY_BUFFER, bufferHandle);
        GL30.glFlushMappedBufferRange(GL15.GL_ARRAY_BUFFER, offset, length);
        getBufferStrategy().bindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
}


