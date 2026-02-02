package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.util.KeyId;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pool for MeshHolder instances to avoid creating new objects every frame.
 * Uses mesh handle as key for efficient lookup.
 */
public class MeshHolderPool {
    private static final MeshHolderPool INSTANCE = new MeshHolderPool();
    
    private final Map<Long, MeshHolder> pool = new ConcurrentHashMap<>();
    
    public static MeshHolderPool getInstance() {
        return INSTANCE;
    }
    
    private MeshHolderPool() {}
    
    /**
     * Get or create a MeshHolder for the given mesh.
     * Returns the cached instance if available.
     */
    public MeshHolder get(@Nullable PreparedMesh mesh) {
        if (mesh == null) {
            return MeshHolder.EMPTY;
        }
        
        if (mesh instanceof BakedTypeMesh baked) {
            long handle = baked.getVAOHandle();
            return pool.computeIfAbsent(handle, h -> new MeshHolder(mesh));
        }
        
        // For non-baked meshes, return EMPTY as they don't have stable handles
        return MeshHolder.EMPTY;
    }
    
    /**
     * Clear the pool. Call this on resource reload or cleanup.
     */
    public void clear() {
        pool.clear();
    }
    
    /**
     * Remove a specific mesh from the pool.
     */
    public void remove(long meshHandle) {
        pool.remove(meshHandle);
    }
    
    /**
     * Get the current pool size.
     */
    public int size() {
        return pool.size();
    }
    
    /**
     * Holder for mesh data with cached hash and equality.
     * Instances are pooled to avoid per-frame allocation.
     */
    public static class MeshHolder {
        public static final MeshHolder EMPTY = new MeshHolder();
        
        private final BakedTypeMesh mesh;
        private final long meshHandle;
        private final KeyId meshId;
        private final int hash;
        
        // Private constructor for EMPTY
        private MeshHolder() {
            this.mesh = null;
            this.meshHandle = -1;
            this.meshId = null;
            this.hash = Objects.hash(-1L, null);
        }
        
        MeshHolder(@Nullable PreparedMesh mesh) {
            if (mesh instanceof BakedTypeMesh baked) {
                this.meshHandle = baked.getVAOHandle();
                this.meshId = baked.getKetId();
                this.mesh = baked;
            } else {
                this.meshHandle = -1;
                this.mesh = null;
                this.meshId = null;
            }
            this.hash = Objects.hash(meshHandle, meshId);
        }
        
        @Nullable
        public BakedTypeMesh bakedTypeMesh() {
            return mesh;
        }
        
        @Nullable
        public KeyId meshId() {
            return meshId;
        }
        
        public long getMeshHandle() {
            return meshHandle;
        }
        
        public boolean isEmpty() {
            return mesh == null;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MeshHolder that = (MeshHolder) o;
            return meshHandle == that.meshHandle && Objects.equals(meshId, that.meshId);
        }
        
        @Override
        public int hashCode() {
            return hash;
        }
        
        @Override
        public String toString() {
            return "MeshHolder{handle=" + meshHandle + ", id=" + meshId + "}";
        }
    }
}

