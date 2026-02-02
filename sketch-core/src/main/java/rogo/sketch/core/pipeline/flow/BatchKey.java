package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.impl.MeshHolderPool;

import java.util.Objects;

/**
 * Composite key for batch grouping, combining RenderSetting and Mesh identity.
 * Used for pre-allocating and looking up render batches.
 */
public final class BatchKey {
    private final RenderSetting renderSetting;
    private final long meshHandle;
    private final int hash;
    
    public BatchKey(RenderSetting renderSetting, MeshHolderPool.MeshHolder meshHolder) {
        this.renderSetting = renderSetting;
        this.meshHandle = meshHolder != null ? meshHolder.getMeshHandle() : -1;
        this.hash = Objects.hash(renderSetting, meshHandle);
    }
    
    public BatchKey(RenderSetting renderSetting, long meshHandle) {
        this.renderSetting = renderSetting;
        this.meshHandle = meshHandle;
        this.hash = Objects.hash(renderSetting, meshHandle);
    }
    
    public RenderSetting getRenderSetting() {
        return renderSetting;
    }
    
    public long getMeshHandle() {
        return meshHandle;
    }
    
    public boolean hasMesh() {
        return meshHandle >= 0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchKey batchKey = (BatchKey) o;
        return meshHandle == batchKey.meshHandle && Objects.equals(renderSetting, batchKey.renderSetting);
    }
    
    @Override
    public int hashCode() {
        return hash;
    }
    
    @Override
    public String toString() {
        return "BatchKey{setting=" + renderSetting + ", meshHandle=" + meshHandle + "}";
    }
}

