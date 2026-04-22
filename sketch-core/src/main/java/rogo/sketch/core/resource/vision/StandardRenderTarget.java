package rogo.sketch.core.resource.vision;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import rogo.sketch.core.api.Resizable;
import rogo.sketch.core.backend.GpuHandle;
import rogo.sketch.core.resource.descriptor.RenderTargetResolutionMode;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

public class StandardRenderTarget extends RenderTarget implements Resizable, AttachmentBackedRenderTarget {
    private final List<KeyId> colorAttachmentIds = new ArrayList<>();
    private KeyId depthAttachmentId;
    private KeyId stencilAttachmentId;

    // Resolution management
    private final RenderTargetResolutionMode resolutionMode;
    private final int baseWidth, baseHeight;
    private final float scaleX, scaleY;

    /**
     * @deprecated Use {@link #StandardRenderTarget(GpuHandle, KeyId, ResolvedRenderTargetSpec, KeyId)} so GL names do not leak into shared resource code.
     */
    @Deprecated
    public StandardRenderTarget(int handle, KeyId keyId, ResolvedRenderTargetSpec descriptor, @Nullable KeyId linkedTexture) {
        super(handle, keyId, descriptor);
        RenderTargetResolutionMode mode = descriptor.resolutionMode();
        this.resolutionMode = mode;
        this.scaleX = descriptor.scaleX();
        this.scaleY = descriptor.scaleY();

        Vector2i dimension = updateDimensions(descriptor.baseWidth(), descriptor.baseHeight(), linkedTexture);
        this.baseWidth = dimension.x;
        this.baseHeight = dimension.y;
        for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
            setColorAttachment(i, descriptor.colorAttachments().get(i));
        }
        if (descriptor.depthAttachment() != null) {
            setDepthAttachment(descriptor.depthAttachment());
        }
        if (descriptor.stencilAttachment() != null) {
            setStencilAttachment(descriptor.stencilAttachment());
        }
    }

    public StandardRenderTarget(GpuHandle handle, KeyId keyId, ResolvedRenderTargetSpec descriptor, @Nullable KeyId linkedTexture) {
        super(handle, keyId, descriptor);
        RenderTargetResolutionMode mode = descriptor.resolutionMode();
        this.resolutionMode = mode;
        this.scaleX = descriptor.scaleX();
        this.scaleY = descriptor.scaleY();

        Vector2i dimension = updateDimensions(descriptor.baseWidth(), descriptor.baseHeight(), linkedTexture);
        this.baseWidth = dimension.x;
        this.baseHeight = dimension.y;
        for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
            setColorAttachment(i, descriptor.colorAttachments().get(i));
        }
        if (descriptor.depthAttachment() != null) {
            setDepthAttachment(descriptor.depthAttachment());
        }
        if (descriptor.stencilAttachment() != null) {
            setStencilAttachment(descriptor.stencilAttachment());
        }
    }

    /**
     * Convenience constructor for fixed resolution
     *
     * @deprecated Use {@link #StandardRenderTarget(GpuHandle, KeyId, ResolvedRenderTargetSpec, KeyId)} with an explicit fixed descriptor.
     */
    @Deprecated
    public StandardRenderTarget(int handle, KeyId keyId, int width, int height) {
        this(handle, keyId, new ResolvedRenderTargetSpec(
                keyId,
                RenderTargetResolutionMode.FIXED,
                width,
                height,
                1.0f,
                1.0f,
                List.of(),
                null,
                null), null);
    }

    public Vector2i updateDimensions(int baseWidth, int baseHeight, @Nullable KeyId linkedTexture) {
        Vector2i dimensions = new Vector2i(baseWidth, baseHeight);

        if (linkedTexture != null) {
            Texture texture = resolveLinkedTexture(linkedTexture);
            if (texture != null && !texture.isDisposed()) {
                int w = texture.getCurrentWidth();
                int h = texture.getCurrentHeight();
                if (w > 0 && h > 0) {
                    dimensions.x = w;
                    dimensions.y = h;
                    resize(w, h);
                }
            }
        }

        if (dimensions.x != currentWidth || dimensions.y != currentHeight) {
            resize(dimensions.x, dimensions.y);
        }

        return dimensions;
    }

    protected @Nullable Texture resolveLinkedTexture(KeyId linkedTexture) {
        return null;
    }

    /**
     * Set color attachment by index
     */
    public void setColorAttachment(int index, KeyId textureId) {
        while (colorAttachmentIds.size() <= index) {
            colorAttachmentIds.add(null);
        }

        colorAttachmentIds.set(index, textureId);
    }

    /**
     * Set depth attachment
     */
    public void setDepthAttachment(KeyId textureId) {
        this.depthAttachmentId = textureId;
    }

    /**
     * Set stencil attachment
     */
    public void setStencilAttachment(KeyId textureId) {
        this.stencilAttachmentId = textureId;
    }

    @Override
    public void dispose() {
        markDisposed();
    }

    public int getCurrentWidth() {
        return currentWidth;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    public RenderTargetResolutionMode getResolutionMode() {
        return resolutionMode;
    }

    public int getBaseWidth() {
        return baseWidth;
    }

    public int getBaseHeight() {
        return baseHeight;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public List<KeyId> getColorAttachmentIds() {
        return new ArrayList<>(colorAttachmentIds);
    }

    public KeyId getDepthAttachmentId() {
        return depthAttachmentId;
    }

    public KeyId getStencilAttachmentId() {
        return stencilAttachmentId;
    }

    protected void attachAllAttachments() {
        // Backend-installed targets may override to attach native image views or
        // framebuffers once all attachment ids are known.
    }

}

