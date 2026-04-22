package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendResourceRegistry;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.util.KeyId;

final class OpenGLStandardRenderTarget extends StandardRenderTarget
        implements BackendInstalledRenderTarget, OpenGLFramebufferHandleResource {
    private final GraphicsAPI api;
    private final BackendResourceRegistry resourceRegistry;

    OpenGLStandardRenderTarget(
            int handle,
            KeyId keyId,
            ResolvedRenderTargetSpec descriptor,
            GraphicsAPI api,
            BackendResourceRegistry resourceRegistry) {
        super(handle, keyId, descriptor, null);
        this.api = api;
        this.resourceRegistry = resourceRegistry;
        attachAllAttachments();
    }

    @Override
    public void bind() {
        api.bindFrameBuffer(handle.asGlName());
    }

    @Override
    protected void attachAllAttachments() {
        for (int i = 0; i < getColorAttachmentIds().size(); i++) {
            KeyId attachmentId = getColorAttachmentIds().get(i);
            if (attachmentId != null) {
                attachTextureToFramebuffer(attachmentId, GL30.GL_COLOR_ATTACHMENT0 + i);
            }
        }
        if (getDepthAttachmentId() != null) {
            attachTextureToFramebuffer(getDepthAttachmentId(), GL30.GL_DEPTH_ATTACHMENT);
        }
        if (getStencilAttachmentId() != null) {
            attachTextureToFramebuffer(getStencilAttachmentId(), GL30.GL_STENCIL_ATTACHMENT);
        }
    }

    private void attachTextureToFramebuffer(KeyId textureId, int attachment) {
        if (textureId == null) {
            return;
        }
        if (resourceRegistry.resolveTexture(textureId) instanceof OpenGLTextureHandleResource texture) {
            api.framebufferTexture2D(handle.asGlName(), attachment, GL11.GL_TEXTURE_2D, texture.textureHandle(), 0);
        }
    }

    @Override
    public int framebufferHandle() {
        return handle.asGlName();
    }

    @Override
    public void dispose() {
        if (!isDisposed()) {
            api.deleteFramebuffer(handle.asGlName());
            markDisposed();
        }
    }
}

