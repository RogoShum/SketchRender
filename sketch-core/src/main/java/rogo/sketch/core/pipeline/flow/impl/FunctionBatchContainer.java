package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.information.FunctionInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BatchContainer implementation for function-type graphics.
 * <p>
 * Manages RenderBatches grouped only by RenderSetting (no mesh grouping needed).
 * Function graphics execute custom logic and don't require mesh data.
 * </p>
 */
public class FunctionBatchContainer implements BatchContainer<FunctionalGraphics, FunctionInstanceInfo> {
    
    // RenderSetting -> RenderBatch mapping (no mesh grouping for function)
    private final Map<RenderSetting, RenderBatch<FunctionInstanceInfo>> batches = new ConcurrentHashMap<>();
    
    // Graphics -> current RenderSetting for fast lookup on dirty
    private final Map<Graphics, RenderSetting> instanceToRenderSetting = new ConcurrentHashMap<>();
    
    // Graphics -> cached RenderParameter (needed for dirty re-registration)
    private final Map<Graphics, RenderParameter> instanceToRenderParam = new ConcurrentHashMap<>();
    
    // Dirty instances pending reassignment
    private final Set<Graphics> dirtyInstances = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // ===== ContainerListener Implementation =====
    
    @Override
    public void onInstanceAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        if (graphics instanceof FunctionalGraphics function) {
            registerInstance(function, renderParameter);
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
        if (graphics instanceof FunctionalGraphics) {
            dirtyInstances.add(graphics);
        }
    }
    
    // ===== BatchContainer Implementation =====
    
    @Override
    public void registerInstance(FunctionalGraphics graphics, RenderParameter renderParameter) {
        // Store render parameter for potential dirty re-registration
        instanceToRenderParam.put(graphics, renderParameter);
        
        // Compute RenderSetting (function graphics may have null partial setting)
        RenderSetting renderSetting = RenderSetting.fromPartial(renderParameter, graphics.getPartialRenderSetting());
        
        // Get or create batch
        RenderBatch<FunctionInstanceInfo> batch = batches.computeIfAbsent(renderSetting, 
                k -> new RenderBatch<>(renderSetting));
        
        // Create instance info
        FunctionInstanceInfo info = new FunctionInstanceInfo(graphics, renderParameter);
        
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
            RenderBatch<FunctionInstanceInfo> batch = batches.get(setting);
            if (batch != null) {
                batch.removeByGraphics(graphics);
            }
        }
    }
    
    @Override
    public void handleDirtyInstance(FunctionalGraphics graphics) {
        // Get cached render parameter
        RenderParameter renderParameter = instanceToRenderParam.get(graphics);
        if (renderParameter == null) {
            // Not registered, skip
            return;
        }
        
        // Remove from old batch
        RenderSetting oldSetting = instanceToRenderSetting.get(graphics);
        if (oldSetting != null) {
            RenderBatch<FunctionInstanceInfo> oldBatch = batches.get(oldSetting);
            if (oldBatch != null) {
                oldBatch.removeByGraphics(graphics);
            }
        }
        
        // Re-register to potentially new batch
        registerInstance(graphics, renderParameter);
    }
    
    @Override
    public Collection<RenderBatch<FunctionInstanceInfo>> getAllBatches() {
        return batches.values();
    }
    
    @Override
    public Collection<RenderBatch<FunctionInstanceInfo>> getActiveBatches() {
        List<RenderBatch<FunctionInstanceInfo>> active = new ArrayList<>();
        for (RenderBatch<FunctionInstanceInfo> batch : batches.values()) {
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
            if (graphics instanceof FunctionalGraphics function) {
                handleDirtyInstance(function);
            }
        }
        dirtyInstances.clear();
        
        // Clear visible instances on all batches for new frame
        for (RenderBatch<FunctionInstanceInfo> batch : batches.values()) {
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
    public Class<FunctionalGraphics> getGraphicsType() {
        return FunctionalGraphics.class;
    }
    
    @Override
    public Class<FunctionInstanceInfo> getInfoType() {
        return FunctionInstanceInfo.class;
    }
    
    // ===== Helper Methods =====
    
    /**
     * Get the batch for a specific graphics instance.
     */
    @Nullable
    public RenderBatch<FunctionInstanceInfo> getBatchFor(Graphics graphics) {
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

