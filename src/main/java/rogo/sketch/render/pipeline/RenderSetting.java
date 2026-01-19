package rogo.sketch.render.pipeline;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.util.KeyId;

import javax.annotation.Nullable;
import java.util.Objects;

public class RenderSetting implements ResourceObject {
    private final RenderParameter renderParameter;
    private FullRenderState renderState;
    private ResourceBinding resourceBinding;
    private boolean shouldSwitchRenderState;
    private final @Nullable KeyId sourceKeyId;
    private final @Nullable GraphicsResourceManager.ResourceReloadListener reloadListener;
    private boolean disposed = false;

    public RenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter, boolean shouldSwitchRenderState) {
        this(renderState, resourceBinding, renderParameter, shouldSwitchRenderState, null);
    }

    public RenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter, boolean shouldSwitchRenderState, @Nullable KeyId sourcePartialSetting) {
        this.renderState = renderState;
        this.resourceBinding = resourceBinding;
        this.renderParameter = renderParameter == null ? RenderParameter.INVALID : renderParameter;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.sourceKeyId = sourcePartialSetting;

        if (sourcePartialSetting != null) {
            reloadListener = this::onSourceResourceReload;
            setupReloadListener();
        } else {
            reloadListener = null;
        }
    }

    public FullRenderState renderState() {
        return renderState;
    }

    public ResourceBinding resourceBinding() {
        return resourceBinding;
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    private void setupReloadListener() {
        GraphicsResourceManager.getInstance().registerReloadListener(
                ResourceTypes.PARTIAL_RENDER_SETTING,
                sourceKeyId,
                reloadListener
        );
    }

    /**
     * Handle resource reload
     */
    private void onSourceResourceReload(KeyId resourceName, ResourceObject newResource) {
        if (newResource instanceof PartialRenderSetting newSetting) {
            this.renderState = newSetting.renderState;
            this.resourceBinding = newSetting.resourceBinding;
            this.shouldSwitchRenderState = newSetting.shouldSwitchRenderState;
        }
    }

    @Nullable
    public GraphicsResourceManager.ResourceReloadListener reloadListener() {
        return reloadListener;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenderSetting that = (RenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState && Objects.equals(renderState, that.renderState) && Objects.equals(resourceBinding, that.resourceBinding) && Objects.equals(renderParameter, that.renderParameter) && Objects.equals(sourceKeyId, that.sourceKeyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderState, resourceBinding, renderParameter, shouldSwitchRenderState, sourceKeyId);
    }

    @Override
    public int getHandle() {
        return hashCode();
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public static RenderSetting fromPartial(PartialRenderSetting partial, RenderParameter renderParameter) {
        return new RenderSetting(
                partial.renderState(),
                partial.resourceBinding(),
                renderParameter,
                partial.shouldSwitchRenderState(),
                partial.getSourceIdentifier()
        );
    }

    public static RenderSetting basic(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter) {
        return new RenderSetting(
                renderState,
                resourceBinding,
                renderParameter,
                true
        );
    }

    public static RenderSetting computeShader(PartialRenderSetting partial) {
        return new RenderSetting(
                partial.renderState(),
                partial.resourceBinding(),
                new ComputeParameter(),
                partial.shouldSwitchRenderState(),
                partial.getSourceIdentifier()
        );
    }
}