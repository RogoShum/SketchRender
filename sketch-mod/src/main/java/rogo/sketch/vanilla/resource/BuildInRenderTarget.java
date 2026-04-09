package rogo.sketch.vanilla.resource;

import rogo.sketch.backend.opengl.OpenGLFramebufferHandleResource;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.AttachmentBackedRenderTarget;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.function.Supplier;

public class BuildInRenderTarget extends RenderTarget
        implements AttachmentBackedRenderTarget, BackendInstalledRenderTarget, OpenGLFramebufferHandleResource {
    protected final Supplier<Integer> fbId;

    public BuildInRenderTarget(Supplier<Integer> fbId, KeyId keyId, ResolvedRenderTargetSpec descriptor) {
        super(fbId.get(), keyId, descriptor);
        this.fbId = fbId;
    }

    private int resolveHandle() {
        return fbId.get();
    }

    @Override
    public void bind() {
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, resolveHandle());
    }

    @Override
    public void dispose() {

    }

    @Override
    public int framebufferHandle() {
        return resolveHandle();
    }

    @Override
    public List<KeyId> getColorAttachmentIds() {
        return descriptor.colorAttachments();
    }

    @Override
    public KeyId getDepthAttachmentId() {
        return descriptor.depthAttachment();
    }

    @Override
    public KeyId getStencilAttachmentId() {
        return descriptor.stencilAttachment();
    }
}

