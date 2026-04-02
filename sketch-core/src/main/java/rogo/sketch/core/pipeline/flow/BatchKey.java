package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.pipeline.flow.impl.MeshHolderPool;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Legacy persistent batch key kept only while the V2 geometry/state/resource
 * bucket migration is still in progress.
 */
@Deprecated(forRemoval = false)
public final class BatchKey {
    private final RenderSetting renderSetting;
    private final GeometrySourceKey sourceKey;
    private final int hash;
    
    public BatchKey(RenderSetting renderSetting, MeshHolderPool.MeshHolder meshHolder) {
        this.renderSetting = renderSetting;
        this.sourceKey = meshHolder != null ? meshHolder.geometrySourceKey() : GeometrySourceKey.empty();
        this.hash = Objects.hash(renderSetting, sourceKey);
    }
    
    public BatchKey(RenderSetting renderSetting, long meshHandle) {
        this.renderSetting = renderSetting;
        this.sourceKey = new GeometrySourceKey(
                KeyId.of("sketch:legacy_mesh_source"),
                meshHandle,
                0,
                0,
                0,
                0);
        this.hash = Objects.hash(renderSetting, sourceKey);
    }
    
    public RenderSetting getRenderSetting() {
        return renderSetting;
    }

    public GeometrySourceKey getSourceKey() {
        return sourceKey;
    }
    
    public long getMeshHandle() {
        return sourceKey != null ? sourceKey.stableId() : -1L;
    }
    
    public boolean hasMesh() {
        return sourceKey != null && sourceKey.stableId() >= 0L;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchKey batchKey = (BatchKey) o;
        return Objects.equals(sourceKey, batchKey.sourceKey) && Objects.equals(renderSetting, batchKey.renderSetting);
    }
    
    @Override
    public int hashCode() {
        return hash;
    }
    
    @Override
    public String toString() {
        return "BatchKey{setting=" + renderSetting + ", sourceKey=" + sourceKey + "}";
    }
}
