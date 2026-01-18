package rogo.sketch.render.state;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class GLStateQueryUtils {
    // Buffers for querying values (thread-local or allocated on stack would be
    // better, but static utils is requested)
    // We will allocate small buffers inside methods to avoid thread safety issues
    // if multiple threads access this (though GL is single threaded usually).
    // Or we use 1-size arrays for primitive transfers.

    // --- Blend State ---
    public static boolean isBlendEnabled() {
        return GL11.glIsEnabled(GL11.GL_BLEND);
    }

    public static int getBlendSrcFactor() {
        return GL11.glGetInteger(GL11.GL_BLEND_SRC);
    }

    public static int getBlendDstFactor() {
        return GL11.glGetInteger(GL11.GL_BLEND_DST);
    }

    // --- Depth Test State ---
    public static boolean isDepthTestEnabled() {
        return GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    }

    public static int getDepthFunc() {
        return GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    }

    // --- Depth Mask State ---
    public static boolean isDepthMaskEnabled() {
        return GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    }

    // --- Cull State ---
    public static boolean isCullFaceEnabled() {
        return GL11.glIsEnabled(GL11.GL_CULL_FACE);
    }

    public static int getCullFaceMode() {
        return GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
    }

    public static int getFrontFace() {
        return GL11.glGetInteger(GL11.GL_FRONT_FACE);
    }

    // --- Polygon Mode State ---
    public static int[] getPolygonMode() {
        int[] modes = new int[2]; // [0]=Front, [1]=Back. Actually deprecated in core profile but usually returns
                                  // 2 values.
        GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, modes);
        return modes;
    }

    // --- Scissor State ---
    public static boolean isScissorTestEnabled() {
        return GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    }

    public static int[] getScissorBox() {
        int[] box = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
        return box;
    }

    // --- Stencil State ---
    public static boolean isStencilTestEnabled() {
        return GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    }

    public static int getStencilFunc() {
        return GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
    }

    public static int getStencilRef() {
        return GL11.glGetInteger(GL11.GL_STENCIL_REF);
    }

    public static int getStencilValueMask() {
        return GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
    }

    public static int getStencilFail() {
        return GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
    }

    public static int getStencilPassDepthFail() {
        return GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
    }

    public static int getStencilPassDepthPass() {
        return GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
    }

    // --- Viewport State ---
    public static int[] getViewport() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return viewport;
    }

    // --- Color Mask State ---
    public static boolean[] getColorMask() {
        byte[] masks = new byte[4]; // GL_TRUE/GL_FALSE are bytes effectively
        ByteBuffer buffer = ByteBuffer.allocateDirect(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
        boolean[] result = new boolean[4];
        result[0] = buffer.get(0) == 1; // GL_TRUE
        result[1] = buffer.get(1) == 1;
        result[2] = buffer.get(2) == 1;
        result[3] = buffer.get(3) == 1;
        return result;
    }

    // --- Polygon Offset State ---
    public static boolean isPolygonOffsetFillEnabled() {
        return GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
    }

    public static float getPolygonOffsetFactor() {
        return GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
    }

    public static float getPolygonOffsetUnits() {
        return GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
    }

    // --- Logic Op State ---
    public static boolean isLogicOpEnabled() {
        return GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
    }

    public static int getLogicOpMode() {
        return GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE);
    }

    // --- Render Target State (Framebuffer) ---
    public static int getDrawFramebufferBinding() {
        return GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    }
}
