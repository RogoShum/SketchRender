package rogo.sketch.core.instance;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public class DrawCallGraphics extends MeshGraphics implements ResourceObject {
    private final KeyId stageId;
    private final PartialRenderSetting partialRenderSetting;
    private final PreparedMesh mesh;
    private final RenderParameter renderParameter;
    private final long descriptorVersion;
    private boolean disposed = false;

    public DrawCallGraphics(KeyId keyId, KeyId stageId, KeyId partialRenderSetting, PreparedMesh mesh, RenderParameter renderParameter) {
        this(
                keyId,
                stageId,
                (PartialRenderSetting) GraphicsResourceManager.getInstance()
                        .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, partialRenderSetting).get(),
                mesh,
                renderParameter);
    }

    public DrawCallGraphics(
            KeyId keyId,
            KeyId stageId,
            PartialRenderSetting partialRenderSetting,
            PreparedMesh mesh,
            RenderParameter renderParameter) {
        super(keyId);
        this.stageId = stageId;
        this.partialRenderSetting = partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY;
        this.mesh = mesh;
        this.renderParameter = renderParameter;
        this.descriptorVersion = Objects.hash(this.partialRenderSetting);
    }

    public KeyId stageId() {
        return stageId;
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    @Override
    public PreparedMesh getPreparedMesh() {
        return mesh;
    }

    @Override
    public long descriptorVersion() {
        return descriptorVersion;
    }

    @Override
    public CompiledRenderSetting buildRenderDescriptor(RenderParameter renderParameter) {
        return compilePartialDescriptor(renderParameter, partialRenderSetting);
    }

    @Override
    public boolean shouldDiscard() {
        return isDisposed();
    }

    @Override
    public boolean shouldRender() {
        return !isDisposed();
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}

