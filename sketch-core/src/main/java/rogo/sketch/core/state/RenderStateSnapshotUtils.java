package rogo.sketch.core.state;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.state.gl.*;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.Map;

public class RenderStateSnapshotUtils {

    /**
     * Captures the current OpenGL state into a FullRenderState object.
     * Note: This attempts to query the GPU state directly.
     * Some states (like ShaderState or arbitrary RenderTargetState) might not be
     * fully reconstructible
     * from raw GL values without context, and thus might be omitted or
     * approximated.
     */
    public static FullRenderState createSnapshot() {
        Map<KeyId, RenderStateComponent> components = new HashMap<>();

        // Blend State
        components.put(BlendState.TYPE, new BlendState(
                GLStateQueryUtils.isBlendEnabled(),
                GLStateQueryUtils.getBlendSrcFactor(),
                GLStateQueryUtils.getBlendDstFactor()));

        // Depth Test State
        components.put(DepthTestState.TYPE, new DepthTestState(
                GLStateQueryUtils.isDepthTestEnabled(),
                GLStateQueryUtils.getDepthFunc()));

        // Depth Mask State
        components.put(DepthMaskState.TYPE, new DepthMaskState(
                GLStateQueryUtils.isDepthMaskEnabled()));

        // Cull State
        components.put(CullState.TYPE, new CullState(
                GLStateQueryUtils.isCullFaceEnabled(),
                GLStateQueryUtils.getCullFaceMode(),
                GLStateQueryUtils.getFrontFace()));

        // Polygon Mode State
        int[] polyMode = GLStateQueryUtils.getPolygonMode();
        // Assuming front and back are same or taking front, as PolygonModeState stores
        // one mode per one face request?
        // Actually PolygonModeState stores (face, mode).
        // GL returns [front, back].
        // Ideally we should check if they differ. If they differ, PolygonModeState
        // cannot fully represent it
        // because it accepts one face arg in constructor but applies to that face.
        // Wait, PolygonModeState constructor takes (face, mode).
        // But glPolygonMode takes (face, mode).
        // If we want to capture the state, we might need two components if they differ?
        // Current PolygonModeState structure suggests it sets ONE state.
        // We will just capture GL_FRONT_AND_BACK if they are same, or GL_FRONT if they
        // differ (approximation).
        // Common case: both are FILL.
        int frontMode = polyMode[0];
        int backMode = polyMode[1];
        if (frontMode == backMode) {
            components.put(PolygonModeState.TYPE,
                    new PolygonModeState(org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK, frontMode));
        } else {
            // Priority to Front? Or separate? SketchLib might only support one active
            // PolygonModeState entry in the map.
            components.put(PolygonModeState.TYPE, new PolygonModeState(org.lwjgl.opengl.GL11.GL_FRONT, frontMode));
        }

        // Scissor State
        int[] scissor = GLStateQueryUtils.getScissorBox();
        components.put(ScissorState.TYPE, new ScissorState(
                GLStateQueryUtils.isScissorTestEnabled(),
                scissor[0], scissor[1], scissor[2], scissor[3]));

        // Stencil State
        // Stencil is complex because it has separate front/back state in GL, but
        // StencilState class seems to assume unified or simple state.
        // StencilState has one func, one mask, one op set.
        // We will query GL_STENCIL_FUNC (implied Front) etc.
        components.put(StencilState.TYPE, new StencilState(
                GLStateQueryUtils.isStencilTestEnabled(),
                GLStateQueryUtils.getStencilFunc(),
                GLStateQueryUtils.getStencilRef(),
                GLStateQueryUtils.getStencilValueMask(),
                GLStateQueryUtils.getStencilFail(),
                GLStateQueryUtils.getStencilPassDepthFail(),
                GLStateQueryUtils.getStencilPassDepthPass()));

        // Viewport State
        int[] viewport = GLStateQueryUtils.getViewport();
        components.put(ViewportState.TYPE, new ViewportState(
                viewport[0], viewport[1], viewport[2], viewport[3]));

        // Color Mask State
        boolean[] colorMask = GLStateQueryUtils.getColorMask();
        components.put(ColorMaskState.TYPE, new ColorMaskState(
                colorMask[0], colorMask[1], colorMask[2], colorMask[3]));

        // Polygon Offset State
        components.put(PolygonOffsetState.TYPE, new PolygonOffsetState(
                GLStateQueryUtils.isPolygonOffsetFillEnabled(),
                GLStateQueryUtils.getPolygonOffsetFactor(),
                GLStateQueryUtils.getPolygonOffsetUnits()));

        // Logic Op State
        components.put(LogicOpState.TYPE, new LogicOpState(
                GLStateQueryUtils.isLogicOpEnabled(),
                GLStateQueryUtils.getLogicOpMode()));

        // Render Target State
        // Difficult to reconstruct from int ID to ResourceReference without a registry
        // lookup.
        // Omitted for now. The snapshot will not contain RenderTarget state.

        return DefaultRenderStates.createFullRenderState(components);
    }
}
