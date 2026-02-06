package rogo.sketch.core.state.snapshot;

import org.lwjgl.opengl.*;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.state.snapshot.SnapshotScope.StateType;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
    private int[] textureBindings;      // Textures bound to each unit
    private int[] ssboBindings;         // SSBOs bound to each binding point
    private int[] uboBindings;          // UBOs bound to each binding point
    private int[] imageBindings;        // Images bound to each unit
    
    public GLStateSnapshot(SnapshotScope scope) {
        this.scope = scope;
        
        // Allocate arrays based on scope
        this.textureBindings = new int[scope.getTextureUnits().length];
        this.ssboBindings = new int[scope.getSSBOBindings().length];
        this.uboBindings = new int[scope.getUBOBindings().length];
        this.imageBindings = new int[scope.getImageBindings().length];
    }
    
    /**
     * Capture the current GL state based on the scope
     */
    public void capture() {
        // Always capture core bindings if any states are captured
        if (!scope.isEmpty()) {
            if (scope.shouldCapture(StateType.SHADER_PROGRAM)) {
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
        if (scope.shouldCapture(StateType.BLEND)) {
            captureBlendState();
        }
        
        if (scope.shouldCapture(StateType.DEPTH_TEST)) {
            captureDepthTestState();
        }
        
        if (scope.shouldCapture(StateType.DEPTH_MASK)) {
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        }
        
        if (scope.shouldCapture(StateType.CULL)) {
            captureCullState();
        }
        
        if (scope.shouldCapture(StateType.SCISSOR)) {
            captureScissorState();
        }
        
        if (scope.shouldCapture(StateType.STENCIL)) {
            captureStencilState();
        }
        
        if (scope.shouldCapture(StateType.VIEWPORT)) {
            captureViewportState();
        }
        
        if (scope.shouldCapture(StateType.COLOR_MASK)) {
            captureColorMaskState();
        }
        
        if (scope.shouldCapture(StateType.POLYGON_OFFSET)) {
            capturePolygonOffsetState();
        }
        
        if (scope.shouldCapture(StateType.LOGIC_OP)) {
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
        GraphicsAPI api = GraphicsDriver.getCurrentAPI();
        
        // Restore render states based on scope
        if (scope.shouldCapture(StateType.BLEND)) {
            restoreBlendState(api);
        }
        
        if (scope.shouldCapture(StateType.DEPTH_TEST)) {
            restoreDepthTestState(api);
        }
        
        if (scope.shouldCapture(StateType.DEPTH_MASK)) {
            api.depthMask(depthMask);
        }
        
        if (scope.shouldCapture(StateType.CULL)) {
            restoreCullState(api);
        }
        
        if (scope.shouldCapture(StateType.SCISSOR)) {
            restoreScissorState(api);
        }
        
        if (scope.shouldCapture(StateType.STENCIL)) {
            restoreStencilState(api);
        }
        
        if (scope.shouldCapture(StateType.VIEWPORT)) {
            api.viewport(viewportX, viewportY, viewportW, viewportH);
        }
        
        if (scope.shouldCapture(StateType.COLOR_MASK)) {
            api.colorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
        }
        
        if (scope.shouldCapture(StateType.POLYGON_OFFSET)) {
            restorePolygonOffsetState(api);
        }
        
        if (scope.shouldCapture(StateType.LOGIC_OP)) {
            restoreLogicOpState(api);
        }
        
        // Restore resource bindings
        restoreTextureBindings();
        restoreSSBOBindings();
        restoreUBOBindings();
        restoreImageBindings();
        
        // Restore core bindings last
        if (scope.shouldCapture(StateType.SHADER_PROGRAM)) {
            GL20.glUseProgram(boundProgram);
        }
        if (scope.shouldCapture(StateType.VAO)) {
            GL30.glBindVertexArray(boundVAO);
        }
        if (scope.shouldCapture(StateType.FBO)) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, boundDrawFBO);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, boundReadFBO);
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

    //todo ???
    private void captureImageBindings() {
        // Image bindings are harder to query, store 0 if not available
        Arrays.fill(imageBindings, 0);
    }
    
    // ==================== Restore Methods ====================
    
    private void restoreBlendState(GraphicsAPI api) {
        if (blendEnabled) {
            api.enableBlend();
        } else {
            api.disableBlend();
        }
        api.blendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
        api.blendEquationSeparate(blendEquationRGB, blendEquationAlpha);
    }
    
    private void restoreDepthTestState(GraphicsAPI api) {
        if (depthTestEnabled) {
            api.enableDepthTest();
        } else {
            api.disableDepthTest();
        }
        api.depthFunc(depthFunc);
    }
    
    private void restoreCullState(GraphicsAPI api) {
        if (cullFaceEnabled) {
            api.enableCullFace();
        } else {
            api.disableCullFace();
        }
        api.cullFace(cullFaceMode);
        api.frontFace(frontFace);
    }
    
    private void restoreScissorState(GraphicsAPI api) {
        if (scissorEnabled) {
            api.enableScissor(scissorX, scissorY, scissorW, scissorH);
        } else {
            api.disableScissor();
        }
    }
    
    private void restoreStencilState(GraphicsAPI api) {
        if (stencilEnabled) {
            api.enableStencil();
        } else {
            api.disableStencil();
        }
        api.stencilFunc(stencilFunc, stencilRef, stencilValueMask);
        api.stencilOp(stencilFail, stencilPassDepthFail, stencilPassDepthPass);
    }
    
    private void restorePolygonOffsetState(GraphicsAPI api) {
        if (polygonOffsetEnabled) {
            api.enablePolygonOffset();
        } else {
            api.disablePolygonOffset();
        }
        api.polygonOffset(polygonOffsetFactor, polygonOffsetUnits);
    }
    
    private void restoreLogicOpState(GraphicsAPI api) {
        if (logicOpEnabled) {
            api.enableLogicOp();
        } else {
            api.disableLogicOp();
        }
        api.logicOp(logicOpMode);
    }
    
    private void restoreTextureBindings() {
        int[] units = scope.getTextureUnits();
        for (int i = 0; i < units.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBindings[i]);
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
        // Image bindings restoration would require more information
        // Skip for now as the original bindings are hard to query
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


