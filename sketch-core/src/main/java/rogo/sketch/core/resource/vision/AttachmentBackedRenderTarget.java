package rogo.sketch.core.resource.vision;

import rogo.sketch.core.util.KeyId;

import java.util.List;

/**
 * Exposes attachment ids for render targets whose storage is backed by named texture resources.
 * Backends use this metadata to resolve attachment textures without assuming OpenGL framebuffer state.
 */
public interface AttachmentBackedRenderTarget {
    List<KeyId> getColorAttachmentIds();

    KeyId getDepthAttachmentId();

    default KeyId getStencilAttachmentId() {
        return null;
    }
}

