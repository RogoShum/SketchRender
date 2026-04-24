package rogo.sketch.core.pipeline.shadow;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Immutable shadow build input captured on the sync thread for async packet
 * compilation.
 */
public record ShadowPassSnapshot(
        Vector3f lightDirection,
        Matrix4f lightViewMatrix,
        Matrix4f lightProjectionMatrix,
        Matrix4f lightViewProjectionMatrix,
        Vector3f focusCenter,
        float shadowDistance,
        float nearPlane,
        float farPlane,
        int width,
        int height,
        long epoch
) {
    private static final Vector3f DEFAULT_LIGHT_DIRECTION = new Vector3f(0.0f, -1.0f, 0.0f);

    public ShadowPassSnapshot {
        lightDirection = new Vector3f(lightDirection != null ? lightDirection : DEFAULT_LIGHT_DIRECTION);
        lightViewMatrix = new Matrix4f(lightViewMatrix != null ? lightViewMatrix : new Matrix4f());
        lightProjectionMatrix = new Matrix4f(lightProjectionMatrix != null ? lightProjectionMatrix : new Matrix4f());
        lightViewProjectionMatrix = new Matrix4f(lightViewProjectionMatrix != null ? lightViewProjectionMatrix : new Matrix4f());
        focusCenter = new Vector3f(focusCenter != null ? focusCenter : new Vector3f());
        width = Math.max(0, width);
        height = Math.max(0, height);
        epoch = Math.max(0L, epoch);
    }

    public static ShadowPassSnapshot fallback(ShadowFrameView shadowView) {
        ShadowFrameView view = shadowView != null
                ? shadowView
                : ShadowFrameView.unavailable(ShadowProvider.NONE_PROVIDER_ID);
        return new ShadowPassSnapshot(
                DEFAULT_LIGHT_DIRECTION,
                new Matrix4f(),
                new Matrix4f(),
                new Matrix4f(),
                new Vector3f(),
                0.0f,
                0.0f,
                0.0f,
                view.width(),
                view.height(),
                view.epoch());
    }
}
