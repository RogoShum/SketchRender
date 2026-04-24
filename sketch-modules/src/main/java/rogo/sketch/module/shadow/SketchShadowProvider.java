package rogo.sketch.module.shadow;

import org.joml.Matrix4f;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceScope;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ImageUsage;
import rogo.sketch.core.resource.descriptor.RenderTargetResolutionMode;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.pipeline.shadow.ShadowFrameView;
import rogo.sketch.core.pipeline.shadow.ShadowProvider;
import rogo.sketch.core.util.KeyId;

import java.util.EnumSet;
import java.util.List;

public final class SketchShadowProvider implements ShadowProvider {
    public static final KeyId PROVIDER_ID = KeyId.of("sketch_render", "sketch_shadow");
    private static final Matrix4f IDENTITY = new Matrix4f();

    private volatile boolean ownShadowEnabled;
    private volatile int resolution = 2048;
    private volatile long epoch;
    private volatile Texture shadowDepthTexture;
    private volatile RenderTarget shadowRenderTarget;
    private volatile ShadowFrameView currentFrameView = ShadowFrameView.unavailable(PROVIDER_ID);

    @Override
    public KeyId providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ShadowFrameView currentFrameView() {
        return currentFrameView;
    }

    public boolean syncResources(
            GraphicsResourceManager resourceManager,
            ResourceAllocator installer,
            String ownerId,
            ResourceScope scope,
            boolean ownShadowEnabled,
            int resolution) {
        int clampedResolution = Math.max(256, resolution);
        boolean configChanged = this.ownShadowEnabled != ownShadowEnabled || this.resolution != clampedResolution;
        boolean resourcesMissing = ownShadowEnabled
                && (shadowDepthTexture == null || shadowDepthTexture.isDisposed()
                || shadowRenderTarget == null || shadowRenderTarget.isDisposed());
        if (!configChanged && !resourcesMissing) {
            return false;
        }

        this.ownShadowEnabled = ownShadowEnabled;
        this.resolution = clampedResolution;
        unregisterConcreteResources(resourceManager);
        disposeResources();

        if (!ownShadowEnabled || resourceManager == null || installer == null) {
            publishUnavailable();
            return true;
        }

        Texture nextDepthTexture = installer.installTexture(
                ShadowModuleDescriptor.SHADOW_DEPTH_TEXTURE,
                new ResolvedImageResource(
                        ShadowModuleDescriptor.SHADOW_DEPTH_TEXTURE,
                        clampedResolution,
                        clampedResolution,
                        1,
                        ImageFormat.D32_FLOAT,
                        EnumSet.of(ImageUsage.DEPTH_ATTACHMENT, ImageUsage.SAMPLED),
                        SamplerFilter.NEAREST,
                        SamplerFilter.NEAREST,
                        null,
                        SamplerWrap.CLAMP_TO_EDGE,
                        SamplerWrap.CLAMP_TO_EDGE,
                        null),
                null,
                null);
        resourceManager.registerDirect(
                ownerId,
                scope,
                ResourceTypes.TEXTURE,
                ShadowModuleDescriptor.SHADOW_DEPTH_TEXTURE,
                nextDepthTexture);

        RenderTarget nextRenderTarget = installer.installRenderTarget(
                ShadowModuleDescriptor.SHADOW_RENDER_TARGET,
                new ResolvedRenderTargetSpec(
                        ShadowModuleDescriptor.SHADOW_RENDER_TARGET,
                        RenderTargetResolutionMode.FIXED,
                        clampedResolution,
                        clampedResolution,
                        1.0f,
                        1.0f,
                        List.of(),
                        ShadowModuleDescriptor.SHADOW_DEPTH_TEXTURE,
                        null));
        resourceManager.registerDirect(
                ownerId,
                scope,
                ResourceTypes.RENDER_TARGET,
                ShadowModuleDescriptor.SHADOW_RENDER_TARGET,
                nextRenderTarget);

        shadowDepthTexture = nextDepthTexture;
        shadowRenderTarget = nextRenderTarget;
        epoch++;
        currentFrameView = new ShadowFrameView(
                PROVIDER_ID,
                true,
                false,
                ShadowModuleDescriptor.SHADOW_RENDER_TARGET,
                ShadowModuleDescriptor.SHADOW_DEPTH_TEXTURE,
                nextRenderTarget.gpuHandle(),
                IDENTITY,
                IDENTITY,
                clampedResolution,
                clampedResolution,
                epoch);
        return true;
    }

    public Texture shadowDepthTexture() {
        return shadowDepthTexture;
    }

    public RenderTarget shadowRenderTarget() {
        return shadowRenderTarget;
    }

    public void clearPublishedResources(GraphicsResourceManager resourceManager) {
        unregisterConcreteResources(resourceManager);
        disposeResources();
        publishUnavailable();
    }

    private void unregisterConcreteResources(GraphicsResourceManager resourceManager) {
        if (resourceManager == null) {
            return;
        }
        if (resourceManager.hasResource(ResourceTypes.RENDER_TARGET, ShadowModuleDescriptor.SHADOW_RENDER_TARGET)) {
            resourceManager.removeResource(ResourceTypes.RENDER_TARGET, ShadowModuleDescriptor.SHADOW_RENDER_TARGET);
        }
        if (resourceManager.hasResource(ResourceTypes.TEXTURE, ShadowModuleDescriptor.SHADOW_DEPTH_TEXTURE)) {
            resourceManager.removeResource(ResourceTypes.TEXTURE, ShadowModuleDescriptor.SHADOW_DEPTH_TEXTURE);
        }
    }

    private void disposeResources() {
        disposeQuietly(shadowRenderTarget);
        disposeQuietly(shadowDepthTexture);
        shadowRenderTarget = null;
        shadowDepthTexture = null;
    }

    private void publishUnavailable() {
        epoch++;
        currentFrameView = new ShadowFrameView(
                PROVIDER_ID,
                false,
                false,
                null,
                null,
                rogo.sketch.core.backend.GpuHandle.NONE,
                IDENTITY,
                IDENTITY,
                0,
                0,
                epoch);
    }

    private void disposeQuietly(Object resource) {
        if (resource instanceof rogo.sketch.core.api.ResourceObject resourceObject && !resourceObject.isDisposed()) {
            try {
                resourceObject.dispose();
            } catch (Exception ignored) {
            }
        }
    }
}
