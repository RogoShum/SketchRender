package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.DispatchProvider;
import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * BatchContainer implementation for compute shader graphics.
 * <p>
 * Manages RenderBatches grouped only by RenderSetting (no mesh grouping needed).
 * Handles automatic batch assignment when instances are added, removed, or modified.
 * </p>
 */
public class ComputeBatchContainer 
        implements BatchContainer<DispatchableGraphics, ComputeInstanceInfo> {
    
    // RenderSetting -> RenderBatch mapping (no mesh grouping for compute)
    private final Map<RenderSetting, RenderBatch<ComputeInstanceInfo>> batches = new ConcurrentHashMap<>();
    
    // Graphics -> current RenderSetting for fast lookup on dirty
    private final Map<Graphics, RenderSetting> instanceToRenderSetting = new ConcurrentHashMap<>();
    
    // Graphics -> cached RenderParameter (needed for dirty re-registration)
    private final Map<Graphics, RenderParameter> instanceToRenderParam = new ConcurrentHashMap<>();
    
    // Dirty instances pending reassignment
    private final Set<Graphics> dirtyInstances = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // ===== ContainerListener Implementation =====
    
    @Override
    public void onInstanceAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        if (graphics instanceof DispatchableGraphics dispatchable) {
            registerInstance(dispatchable, renderParameter);
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
        if (graphics instanceof DispatchableGraphics) {
            dirtyInstances.add(graphics);
        }
    }
    
    // ===== BatchContainer Implementation =====
    
    @Override
    public void registerInstance(DispatchableGraphics graphics, RenderParameter renderParameter) {
        // Store render parameter for potential dirty re-registration
        instanceToRenderParam.put(graphics, renderParameter);
        
        // Compute RenderSetting
        RenderSetting renderSetting = RenderSetting.fromPartial(renderParameter, graphics.getPartialRenderSetting());
        
        // Get or create batch
        RenderBatch<ComputeInstanceInfo> batch = batches.computeIfAbsent(renderSetting, 
                k -> new RenderBatch<>(renderSetting));
        
        // Create instance info with dispatch command
        BiConsumer<RenderContext, ComputeShader> dispatchCommand = graphics.getDispatchCommand();
        ComputeInstanceInfo info = new ComputeInstanceInfo(graphics, renderSetting, dispatchCommand);
        
        // Add to batch
        batch.addInstance(info);
        
        // Record mapping
        instanceToRenderSetting.put(graphics, renderSetting);
        
        // Clear dirty flag
        dirtyInstances.remove(graphics);
    }
    
    @Override
    public void unregisterInstance(Graphics graphics) {
        RenderSetting setting = instanceToRenderSetting.remove(graphics);
        instanceToRenderParam.remove(graphics);
        dirtyInstances.remove(graphics);
        
        if (setting != null) {
            RenderBatch<ComputeInstanceInfo> batch = batches.get(setting);
            if (batch != null) {
                batch.removeByGraphics(graphics);
            }
        }
    }
    
    @Override
    public void handleDirtyInstance(DispatchableGraphics graphics) {
        // Get cached render parameter
        RenderParameter renderParameter = instanceToRenderParam.get(graphics);
        if (renderParameter == null) {
            // Not registered, skip
            return;
        }
        
        // Remove from old batch
        RenderSetting oldSetting = instanceToRenderSetting.get(graphics);
        if (oldSetting != null) {
            RenderBatch<ComputeInstanceInfo> oldBatch = batches.get(oldSetting);
            if (oldBatch != null) {
                oldBatch.removeByGraphics(graphics);
            }
        }
        
        // Re-register to potentially new batch
        registerInstance(graphics, renderParameter);
    }
    
    @Override
    public Collection<RenderBatch<ComputeInstanceInfo>> getAllBatches() {
        return batches.values();
    }
    
    @Override
    public Collection<RenderBatch<ComputeInstanceInfo>> getActiveBatches() {
        List<RenderBatch<ComputeInstanceInfo>> active = new ArrayList<>();
        for (RenderBatch<ComputeInstanceInfo> batch : batches.values()) {
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
            if (graphics instanceof DispatchableGraphics dispatchable) {
                handleDirtyInstance(dispatchable);
            }
        }
        dirtyInstances.clear();
        
        // Clear visible instances on all batches for new frame
        for (RenderBatch<ComputeInstanceInfo> batch : batches.values()) {
            batch.clearVisibleInstances();
        }
    }
    
    @Override
    public void clear() {
        batches.clear();
        instanceToRenderSetting.clear();
        instanceToRenderParam.clear();
        dirtyInstances.clear();
    }
    
    @Override
    public Class<DispatchableGraphics> getGraphicsType() {
        return DispatchableGraphics.class;
    }
    
    @Override
    public Class<ComputeInstanceInfo> getInfoType() {
        return ComputeInstanceInfo.class;
    }
    
    // ===== Helper Methods =====
    
    /**
     * Get the batch for a specific graphics instance.
     */
    @Nullable
    public RenderBatch<ComputeInstanceInfo> getBatchFor(Graphics graphics) {
        RenderSetting setting = instanceToRenderSetting.get(graphics);
        return setting != null ? batches.get(setting) : null;
    }
    
    /**
     * Get the number of registered instances.
     */
    public int getInstanceCount() {
        return instanceToRenderSetting.size();
    }
    
    /**
     * Get the number of batches.
     */
    public int getBatchCount() {
        return batches.size();
    }
}

