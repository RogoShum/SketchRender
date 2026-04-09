package rogo.sketch.backend.opengl.state.snapshot;

import org.lwjgl.opengl.*;
import rogo.sketch.backend.opengl.OpenGLStateAccess;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope.StateType;

import java.nio.ByteBuffer;

/**
 * Captures and restores OpenGL state for a given scope.
 * Optimized to only capture/restore states defined in the scope.
 */
public class GLStateSnapshot {
    
    private final SnapshotScope scope;
    
    // Core bindings
    private int boundProgram;
    private int boundVAO;
    private int boundDrawFBO;
    private int boundReadFBO;
    
    // Blend state
    private boolean blendEnabled;
    private int blendSrcRGB, blendDstRGB;
    private int blendSrcAlpha, blendDstAlpha;
    private int blendEquationRGB, blendEquationAlpha;
    
    // Depth state
    private boolean depthTestEnabled;
    private int depthFunc;
    private boolean depthMask;
    
    // Cull state
    private boolean cullFaceEnabled;
    private int cullFaceMode;
    private int frontFace;
    
    // Scissor state
    private boolean scissorEnabled;
    private int scissorX, scissorY, scissorW, scissorH;
    
    // Stencil state
    private boolean stencilEnabled;
    private int stencilFunc, stencilRef, stencilValueMask;
    private int stencilFail, stencilPassDepthFail, stencilPassDepthPass;
    
    // Viewport
    private int viewportX, viewportY, viewportW, viewportH;
    
    // Color mask
    private boolean colorMaskR, colorMaskG, colorMaskB, colorMaskA;
    
    // Polygon offset
    private boolean polygonOffsetEnabled;
    private float polygonOffsetFactor, polygonOffsetUnits;
    
    // Logic op
    private boolean logicOpEnabled;
    private int logicOpMode;
    
    // Resource bindings (indexed)
    private int activeTextureUnit;
    private int[] textureBindings;      // Textures bound to each unit
    private int[] ssboBindings;         // SSBOs bound to each binding point
    private int[] uboBindings;          // UBOs bound to each binding point
    private int[] imageBindings;        // Images bound to each unit
    private int[] imageLevels;
    private boolean[] imageLayered;
    private int[] imageLayers;
    private int[] imageAccess;
    private int[] imageFormats;
    
    public GLStateSnapshot(SnapshotScope scope) {
        this.scope = scope;
        
        // Allocate arrays based on scope
        this.textureBindings = new int[scope.getTextureUnits().length];
        this.ssboBindings = new int[scope.getSSBOBindings().length];
        this.uboBindings = new int[scope.getUBOBindings().length];
        this.imageBindings = new int[scope.getImageBindings().length];
        this.imageLevels = new int[scope.getImageBindings().length];
        this.imageLayered = new boolean[scope.getImageBindings().length];
        this.imageLayers = new int[scope.getImageBindings().length];
        this.imageAccess = new int[scope.getImageBindings().length];
        this.imageFormats = new int[scope.getImageBindings().length];
    }
    
    /**
     * Capture the current GL state based on the scope
     */
    public void capture() {
        // Always capture core bindings if any states are captured
        if (!scope.isEmpty()) {
            if (scope.shouldCapture(StateType.PROGRAM)) {
                boundProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            }
            if (scope.shouldCapture(StateType.VAO)) {
                boundVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            }
            if (scope.shouldCapture(StateType.FBO)) {
                boundDrawFBO = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                boundReadFBO = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            }
        }
        
        // Capture render states based on scope
        if (scope.shouldCapture(StateType.PIPELINE_RASTER)) {
            captureBlendState();
            captureDepthTestState();
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            captureCullState();
            captureStencilState();
            captureColorMaskState();
            capturePolygonOffsetState();
        }
        
        if (scope.shouldCapture(StateType.DYNAMIC_STATE)) {
            captureScissorState();
            captureViewportState();
            captureLogicOpState();
        }
        
        // Capture resource bindings
        captureTextureBindings();
        captureSSBOBindings();
        captureUBOBindings();
        captureImageBindings();
    }
    
    /**
     * Restore the captured GL state
     */
    public void restore() {
        restore(null, null);
    }

    public void restore(GraphicsAPI api) {
        restore(null, api);
    }

    public void restore(OpenGLStateAccess stateAccess, GraphicsAPI api) {
        // Restore render states based on scope
        if (scope.shouldCapture(StateType.PIPELINE_RASTER)) {
            if (stateAccess != null) {
                restoreBlendState(stateAccess);
                restoreDepthState(stateAccess);
                restoreCullState(stateAccess);
                restoreStencilState(stateAccess);
                stateAccess.colorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
                restorePolygonOffsetState(stateAccess);
            } else {
                restoreBlendStateNative();
                restoreDepthStateNative();
                restoreCullStateNative();
                restoreStencilStateNative();
                GL11.glColorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
                restorePolygonOffsetStateNative();
            }
        }

        if (scope.shouldCapture(StateType.DYNAMIC_STATE)) {
            if (stateAccess != null) {
                restoreScissorState(stateAccess);
                stateAccess.viewport(viewportX, viewportY, viewportW, viewportH);
                restoreLogicOpState(stateAccess);
            } else {
                restoreScissorStateNative();
                GL11.glViewport(viewportX, viewportY, viewportW, viewportH);
                restoreLogicOpStateNative();
            }
        }
        
        // Restore resource bindings
        restoreTextureBindings();
        restoreSSBOBindings();
        restoreUBOBindings();
        restoreImageBindings();
        
        // Restore core bindings last
        if (scope.shouldCapture(StateType.PROGRAM)) {
            if (api != null) {
                api.getShaderStrategy().useProgram(boundProgram);
            } else {
                GL20.glUseProgram(boundProgram);
            }
        }
        if (scope.shouldCapture(StateType.VAO)) {
            if (stateAccess != null) {
                stateAccess.bindVertexArray(boundVAO);
            } else {
                GL30.glBindVertexArray(boundVAO);
            }
        }
        if (scope.shouldCapture(StateType.FBO)) {
            if (stateAccess != null) {
                stateAccess.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, boundDrawFBO);
                stateAccess.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, boundReadFBO);
            } else {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, boundDrawFBO);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, boundReadFBO);
            }
        }
    }
    
    // ==================== Capture Methods ====================
    
    private void captureBlendState() {
        blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        blendSrcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        blendEquationRGB = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    }
    
    private void captureDepthTestState() {
        depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    }
    
    private void captureCullState() {
        cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        cullFaceMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
    }
    
    private void captureScissorState() {
        scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] box = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
        scissorX = box[0];
        scissorY = box[1];
        scissorW = box[2];
        scissorH = box[3];
    }
    
    private void captureStencilState() {
        stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        stencilPassDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        stencilPassDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
    }
    
    private void captureViewportState() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        viewportX = viewport[0];
        viewportY = viewport[1];
        viewportW = viewport[2];
        viewportH = viewport[3];
    }
    
    private void captureColorMaskState() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
        colorMaskR = buffer.get(0) == 1;
        colorMaskG = buffer.get(1) == 1;
        colorMaskB = buffer.get(2) == 1;
        colorMaskA = buffer.get(3) == 1;
    }
    
    private void capturePolygonOffsetState() {
        polygonOffsetEnabled = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        polygonOffsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        polygonOffsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
    }
    
    private void captureLogicOpState() {
        logicOpEnabled = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
        logicOpMode = GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE);
    }
    
    private void captureTextureBindings() {
        int[] units = scope.getTextureUnits();
        if (units.length > 0) {
            activeTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        }
        for (int i = 0; i < units.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
            textureBindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
    }
    
    private void captureSSBOBindings() {
        int[] bindings = scope.getSSBOBindings();
        for (int i = 0; i < bindings.length; i++) {
            ssboBindings[i] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, bindings[i]);
        }
    }
    
    private void captureUBOBindings() {
        int[] bindings = scope.getUBOBindings();
        for (int i = 0; i < bindings.length; i++) {
            uboBindings[i] = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, bindings[i]);
        }
    }

    private void captureImageBindings() {
        int[] bindings = scope.getImageBindings();
        for (int i = 0; i < bindings.length; i++) {
            int binding = bindings[i];
            imageBindings[i] = GL42.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME, binding);
            imageLevels[i] = GL42.glGetIntegeri(GL42.GL_IMAGE_BINDING_LEVEL, binding);
            imageLayered[i] = GL42.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED, binding) == GL11.GL_TRUE;
            imageLayers[i] = GL42.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYER, binding);
            imageAccess[i] = GL42.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS, binding);
            imageFormats[i] = GL42.glGetIntegeri(GL42.GL_IMAGE_BINDING_FORMAT, binding);
        }
    }
    
    // ==================== Restore Methods ====================
    
    private void restoreBlendState(OpenGLStateAccess stateAccess) {
        if (blendEnabled) {
            stateAccess.enableBlend();
        } else {
            stateAccess.disableBlend();
        }
        stateAccess.blendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
        stateAccess.blendEquationSeparate(blendEquationRGB, blendEquationAlpha);
    }

    private void restoreBlendStateNative() {
        if (blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL14.glBlendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
        GL20.glBlendEquationSeparate(blendEquationRGB, blendEquationAlpha);
    }
    
    private void restoreDepthState(OpenGLStateAccess stateAccess) {
        if (depthTestEnabled) {
            stateAccess.enableDepthTest();
        } else {
            stateAccess.disableDepthTest();
        }
        stateAccess.depthFunc(depthFunc);
        stateAccess.depthMask(depthMask);
    }

    private void restoreDepthStateNative() {
        if (depthTestEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthFunc(depthFunc);
        GL11.glDepthMask(depthMask);
    }
    
    private void restoreCullState(OpenGLStateAccess stateAccess) {
        if (cullFaceEnabled) {
            stateAccess.enableCullFace();
        } else {
            stateAccess.disableCullFace();
        }
        stateAccess.cullFace(cullFaceMode);
        stateAccess.frontFace(frontFace);
    }

    private void restoreCullStateNative() {
        if (cullFaceEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        GL11.glCullFace(cullFaceMode);
        GL11.glFrontFace(frontFace);
    }
    
    private void restoreScissorState(OpenGLStateAccess stateAccess) {
        if (scissorEnabled) {
            stateAccess.enableScissor(scissorX, scissorY, scissorW, scissorH);
        } else {
            stateAccess.disableScissor();
        }
    }

    private void restoreScissorStateNative() {
        if (scissorEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
    
    private void restoreStencilState(OpenGLStateAccess stateAccess) {
        if (stencilEnabled) {
            stateAccess.enableStencil();
        } else {
            stateAccess.disableStencil();
        }
        stateAccess.stencilFunc(stencilFunc, stencilRef, stencilValueMask);
        stateAccess.stencilOp(stencilFail, stencilPassDepthFail, stencilPassDepthPass);
    }

    private void restoreStencilStateNative() {
        if (stencilEnabled) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
        GL11.glStencilOp(stencilFail, stencilPassDepthFail, stencilPassDepthPass);
    }
    
    private void restorePolygonOffsetState(OpenGLStateAccess stateAccess) {
        if (polygonOffsetEnabled) {
            stateAccess.enablePolygonOffset();
        } else {
            stateAccess.disablePolygonOffset();
        }
        stateAccess.polygonOffset(polygonOffsetFactor, polygonOffsetUnits);
    }

    private void restorePolygonOffsetStateNative() {
        if (polygonOffsetEnabled) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        } else {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
    }
    
    private void restoreLogicOpState(OpenGLStateAccess stateAccess) {
        if (logicOpEnabled) {
            stateAccess.enableLogicOp();
        } else {
            stateAccess.disableLogicOp();
        }
        stateAccess.logicOp(logicOpMode);
    }

    private void restoreLogicOpStateNative() {
        if (logicOpEnabled) {
            GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
        } else {
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        }
        GL11.glLogicOp(logicOpMode);
    }
    
    private void restoreTextureBindings() {
        int[] units = scope.getTextureUnits();
        for (int i = 0; i < units.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBindings[i]);
        }
        if (units.length > 0) {
            GL13.glActiveTexture(activeTextureUnit);
        }
    }
    
    private void restoreSSBOBindings() {
        int[] bindings = scope.getSSBOBindings();
        for (int i = 0; i < bindings.length; i++) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindings[i], ssboBindings[i]);
        }
    }
    
    private void restoreUBOBindings() {
        int[] bindings = scope.getUBOBindings();
        for (int i = 0; i < bindings.length; i++) {
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, bindings[i], uboBindings[i]);
        }
    }
    
    private void restoreImageBindings() {
        int[] bindings = scope.getImageBindings();
        for (int i = 0; i < bindings.length; i++) {
            GL42.glBindImageTexture(
                    bindings[i],
                    imageBindings[i],
                    imageLevels[i],
                    imageLayered[i],
                    imageLayers[i],
                    imageAccess[i],
                    imageFormats[i]);
        }
    }
    
    // ==================== Accessors ====================
    
    public SnapshotScope getScope() {
        return scope;
    }
    
    public int getBoundProgram() {
        return boundProgram;
    }
    
    public int getBoundVAO() {
        return boundVAO;
    }
    
    public int getBoundDrawFBO() {
        return boundDrawFBO;
    }
}



