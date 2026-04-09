package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.util.KeyId;

final class OpenGLStandardRenderTarget extends StandardRenderTarget
        implements BackendInstalledRenderTarget, OpenGLFramebufferHandleResource {
    private final GraphicsAPI api;
    private final OpenGLBackendResourceResolver resourceResolver;

    OpenGLStandardRenderTarget(
            int handle,
            KeyId keyId,
            ResolvedRenderTargetSpec descriptor,
            GraphicsAPI api,
            OpenGLBackendResourceResolver resourceResolver) {
        super(handle, keyId, descriptor, null);
        this.api = api;
        this.resourceResolver = resourceResolver;
        attachAllAttachments();
    }

    @Override
    public void bind() {
        api.bindFrameBuffer(handle);
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
        if (resourceResolver.resolveTexture(textureId) instanceof OpenGLTextureHandleResource texture) {
            api.framebufferTexture2D(handle, attachment, GL11.GL_TEXTURE_2D, texture.textureHandle(), 0);
        }
    }

    @Override
    public int framebufferHandle() {
        return handle;
    }

    @Override
    public void dispose() {
        if (!isDisposed()) {
            api.deleteFramebuffer(handle);
            markDisposed();
        }
    }
}

