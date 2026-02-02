package rogo.sketch.core.instance;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

public class DrawCallGraphics extends MeshGraphics implements ResourceObject {
    private final PartialRenderSetting partialRenderSetting;
    private final PreparedMesh mesh;
    private boolean disposed = false;

    public DrawCallGraphics(KeyId keyId, KeyId partialRenderSetting, PreparedMesh mesh) {
        super(keyId);
        this.partialRenderSetting = (PartialRenderSetting) GraphicsResourceManager.getInstance()
                .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, partialRenderSetting).get();
        this.mesh = mesh;
    }

    @Override
    public PreparedMesh getPreparedMesh() {
        return mesh;
    }

    @Override
    public void fillVertex(KeyId componentKey, VertexStreamBuilder builder) {

    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        return partialRenderSetting;
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
    public int getHandle() {
        return 0;
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