package rogo.sketch.core.pipeline.shadow;

import org.joml.Matrix4f;
import rogo.sketch.core.backend.GpuHandle;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Immutable shadow-frame metadata shared by core systems and UI diagnostics.
 * Backend-specific binding is intentionally outside this contract.
 */
public final class ShadowFrameView {
    private static final Matrix4f IDENTITY = new Matrix4f();

    private final KeyId providerId;
    private final boolean available;
    private final boolean shadowPassActive;
    private final KeyId renderTargetId;
    private final KeyId shadowMapTextureId;
    private final GpuHandle nativeTargetHandle;
    private final Matrix4f lightViewMatrix;
    private final Matrix4f lightProjectionMatrix;
    private final int width;
    private final int height;
    private final long epoch;

    public ShadowFrameView(
            KeyId providerId,
            boolean available,
            boolean shadowPassActive,
            KeyId renderTargetId,
            KeyId shadowMapTextureId,
            GpuHandle nativeTargetHandle,
            Matrix4f lightViewMatrix,
            Matrix4f lightProjectionMatrix,
            int width,
            int height,
            long epoch) {
        this.providerId = providerId != null ? providerId : ShadowProvider.NONE_PROVIDER_ID;
        this.available = available;
        this.shadowPassActive = shadowPassActive;
        this.renderTargetId = renderTargetId;
        this.shadowMapTextureId = shadowMapTextureId;
        this.nativeTargetHandle = nativeTargetHandle != null ? nativeTargetHandle : GpuHandle.NONE;
        this.lightViewMatrix = new Matrix4f(lightViewMatrix != null ? lightViewMatrix : IDENTITY);
        this.lightProjectionMatrix = new Matrix4f(lightProjectionMatrix != null ? lightProjectionMatrix : IDENTITY);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.epoch = Math.max(0L, epoch);
    }

    public static ShadowFrameView unavailable(KeyId providerId) {
        return new ShadowFrameView(
                providerId,
                false,
                false,
                null,
                null,
                GpuHandle.NONE,
                IDENTITY,
                IDENTITY,
                0,
                0,
                0L);
    }

    public KeyId providerId() {
        return providerId;
    }

    public boolean available() {
        return available;
    }

    public boolean shadowPassActive() {
        return shadowPassActive;
    }

    public KeyId renderTargetId() {
        return renderTargetId;
    }

    public KeyId shadowMapTextureId() {
        return shadowMapTextureId;
    }

    public GpuHandle nativeTargetHandle() {
        return nativeTargetHandle;
    }

    public Matrix4f lightViewMatrix() {
        return new Matrix4f(lightViewMatrix);
    }

    public Matrix4f lightProjectionMatrix() {
        return new Matrix4f(lightProjectionMatrix);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long epoch() {
        return epoch;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ShadowFrameView other)) {
            return false;
        }
        return available == other.available
                && shadowPassActive == other.shadowPassActive
                && width == other.width
                && height == other.height
                && epoch == other.epoch
                && Objects.equals(providerId, other.providerId)
                && Objects.equals(renderTargetId, other.renderTargetId)
                && Objects.equals(shadowMapTextureId, other.shadowMapTextureId)
                && Objects.equals(nativeTargetHandle, other.nativeTargetHandle)
                && Objects.equals(lightViewMatrix, other.lightViewMatrix)
                && Objects.equals(lightProjectionMatrix, other.lightProjectionMatrix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                providerId,
                available,
                shadowPassActive,
                renderTargetId,
                shadowMapTextureId,
                nativeTargetHandle,
                lightViewMatrix,
                lightProjectionMatrix,
                width,
                height,
                epoch);
    }

    @Override
    public String toString() {
        return "ShadowFrameView{" +
                "providerId=" + providerId +
                ", available=" + available +
                ", shadowPassActive=" + shadowPassActive +
                ", renderTargetId=" + renderTargetId +
                ", shadowMapTextureId=" + shadowMapTextureId +
                ", nativeTargetHandle=" + nativeTargetHandle +
                ", width=" + width +
                ", height=" + height +
                ", epoch=" + epoch +
                '}';
    }
}
