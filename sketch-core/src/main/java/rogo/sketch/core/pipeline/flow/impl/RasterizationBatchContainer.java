package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.BatchKey;
import rogo.sketch.core.pipeline.flow.MeshRenderBatch;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.information.RasterizationInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BatchContainer implementation for rasterization graphics.
 * <p>
 * Manages RenderBatches grouped by RenderSetting and Mesh identity.
 * Handles automatic batch assignment when instances are added, removed, or modified.
 * </p>
 */
public class RasterizationBatchContainer 
        implements BatchContainer<MeshBasedGraphics, RasterizationInstanceInfo> {
    
    // BatchKey -> RenderBatch mapping
    private final Map<BatchKey, RenderBatch<RasterizationInstanceInfo>> batches = new ConcurrentHashMap<>();
    
    // Graphics -> current BatchKey for fast lookup on dirty
    private final Map<Graphics, BatchKey> instanceToBatchKey = new ConcurrentHashMap<>();
    
    // Graphics -> cached RenderParameter (needed for dirty re-registration)
    private final Map<Graphics, RenderParameter> instanceToRenderParam = new ConcurrentHashMap<>();
    
    // Dirty instances pending reassignment
    private final Set<Graphics> dirtyInstances = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // ===== ContainerListener Implementation =====
    
    @Override
    public void onInstanceAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        if (graphics instanceof MeshBasedGraphics rasterizable) {
            registerInstance(rasterizable, renderParameter);
        } else {
            throw new IllegalArgumentException("Unsupported graphics type: " + graphics.getClass().getName());
        }
    }
    
    @Override
    public void onInstanceRemoved(Graphics graphics) {
        unregisterInstance(graphics);
    }
    
    @Override
    public void onInstanceDirty(Graphics graphics) {
        if (graphics instanceof MeshBasedGraphics) {
            dirtyInstances.add(graphics);
        }
    }
    
    // ===== BatchContainer Implementation =====
    
    @Override
    public void registerInstance(MeshBasedGraphics graphics, RenderParameter renderParameter) {
        // Store render parameter for potential dirty re-registration
        instanceToRenderParam.put(graphics, renderParameter);
        
        // Compute RenderSetting
        RenderSetting renderSetting = RenderSetting.fromPartial(renderParameter, graphics.getPartialRenderSetting());
        
        // Get mesh holder
        PreparedMesh mesh = graphics.getPreparedMesh();
        MeshHolderPool.MeshHolder meshHolder = MeshHolderPool.getInstance().get(mesh);
        
        // Compute batch key
        BatchKey key = new BatchKey(renderSetting, meshHolder);

        // Get or create batch
        RenderBatch<RasterizationInstanceInfo> batch = batches.computeIfAbsent(key, 
                k -> {
                    if (meshHolder.bakedTypeMesh() != null) {
                        return new MeshRenderBatch(renderSetting, meshHolder.bakedTypeMesh());
                    } else {
                        return new RenderBatch<>(renderSetting);
                    }
                });
        
        // Create instance info
        RasterizationInstanceInfo info = createInstanceInfo(graphics, renderSetting, mesh);
        
        // Add to batch
        batch.addInstance(info);
        
        // Record mappings
        instanceToBatchKey.put(graphics, key);
        
        // Clear dirty flag
        dirtyInstances.remove(graphics);
    }
    
    @Override
    public void unregisterInstance(Graphics graphics) {
        BatchKey key = instanceToBatchKey.remove(graphics);
        instanceToRenderParam.remove(graphics);
        dirtyInstances.remove(graphics);
        
        if (key != null) {
            RenderBatch<RasterizationInstanceInfo> batch = batches.get(key);
            if (batch != null) {
                batch.removeByGraphics(graphics);
            }
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void handleDirtyInstance(MeshBasedGraphics graphics) {
        // Get cached render parameter
        RenderParameter renderParameter = instanceToRenderParam.get(graphics);
        if (renderParameter == null) {
            // Not registered, skip
            return;
        }
        
        // Remove from old batch
        BatchKey oldKey = instanceToBatchKey.get(graphics);
        if (oldKey != null) {
            RenderBatch<RasterizationInstanceInfo> oldBatch = batches.get(oldKey);
            if (oldBatch != null) {
                oldBatch.removeByGraphics(graphics);
            }
        }
        
        // Re-register to potentially new batch
        registerInstance(graphics, renderParameter);
    }
    
    @Override
    public Collection<RenderBatch<RasterizationInstanceInfo>> getAllBatches() {
        return batches.values();
    }
    
    @Override
    public Collection<RenderBatch<RasterizationInstanceInfo>> getActiveBatches() {
        List<RenderBatch<RasterizationInstanceInfo>> active = new ArrayList<>();
        for (RenderBatch<RasterizationInstanceInfo> batch : batches.values()) {
            if (!batch.isEmpty()) {
                active.add(batch);
            }
        }
        return active;
    }
    
    @Override
    public void prepareForFrame() {
        // Process dirty instances
        for (Graphics graphics : dirtyInstances) {
            if (graphics instanceof MeshBasedGraphics rasterizable) {
                handleDirtyInstance(rasterizable);
            }
        }
        dirtyInstances.clear();
        
        // Clear visible instances on all batches for new frame
        for (RenderBatch<RasterizationInstanceInfo> batch : batches.values()) {
            batch.clearVisibleInstances();
        }
    }
    
    @Override
    public void clear() {
        batches.clear();
        instanceToBatchKey.clear();
        instanceToRenderParam.clear();
        dirtyInstances.clear();
    }
    
    @Override
    public Class<MeshBasedGraphics> getGraphicsType() {
        return MeshBasedGraphics.class;
    }
    
    @Override
    public Class<RasterizationInstanceInfo> getInfoType() {
        return RasterizationInstanceInfo.class;
    }
    
    // ===== Helper Methods =====
    
    /**
     * Create a RasterizationInstanceInfo for the given graphics.
     */
    private RasterizationInstanceInfo createInstanceInfo(
            MeshBasedGraphics graphics,
            RenderSetting renderSetting,
            @Nullable PreparedMesh mesh) {
        int vertexCount = mesh != null ? mesh.getVertexCount() : 0;
        return new RasterizationInstanceInfo(graphics, renderSetting, mesh, vertexCount);
    }
    
    /**
     * Get the batch for a specific graphics instance.
     */
    @Nullable
    public RenderBatch<RasterizationInstanceInfo> getBatchFor(Graphics graphics) {
        BatchKey key = instanceToBatchKey.get(graphics);
        return key != null ? batches.get(key) : null;
    }
    
    /**
     * Get the number of registered instances.
     */
    public int getInstanceCount() {
        return instanceToBatchKey.size();
    }
    
    /**
     * Get the number of batches.
     */
    public int getBatchCount() {
        return batches.size();
    }
}

