package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.UniformBatchGroup;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.UniformHook;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.state.gl.ShaderState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a batch of graphics instances that share the same render settings.
 * <p>
 * Supports both immutable (legacy) and mutable modes for incremental updates.
 * </p>
 *
 * @param <T> The type of instance info contained in this batch
 */
public class RenderBatch<T extends InstanceInfo<?>> {
    private final RenderSetting renderSetting;
    private final List<T> instances;
    private final List<UniformBatchGroup> uniformBatches;

    // Cached shader provider for uniform optimization
    private ShaderProvider cachedShaderProvider;
    private List<UniformHook<?>> cachedMatchingHooks;
    private boolean uniformsDirty = true;

    // Visible instances for current frame (subset of all instances)
    private List<T> visibleInstances;

    /**
     * Create an empty batch for pre-allocation (mutable mode).
     */
    public RenderBatch(RenderSetting renderSetting) {
        this.renderSetting = renderSetting;
        this.instances = new ArrayList<>();
        this.uniformBatches = new ArrayList<>();
        this.visibleInstances = new ArrayList<>();
    }

    public RenderSetting getRenderSetting() {
        return renderSetting;
    }

    public List<T> getInstances() {
        return instances;
    }

    /**
     * Get the visible instances for current frame.
     */
    public List<T> getVisibleInstances() {
        return visibleInstances != null ? visibleInstances : instances;
    }

    public int getInstanceCount() {
        return instances.size();
    }

    public int getVisibleInstanceCount() {
        return visibleInstances != null ? visibleInstances.size() : instances.size();
    }

    public List<UniformBatchGroup> getUniformBatches() {
        if (uniformsDirty) {
            collectUniformBatches();
        }
        return uniformBatches;
    }

    // ===== Mutable Instance Management =====

    /**
     * Add an instance to this batch.
     */
    public void addInstance(T instance) {
        instances.add(instance);
        uniformsDirty = true;
    }

    /**
     * Remove an instance from this batch.
     */
    public boolean removeInstance(T instance) {
        boolean removed = instances.remove(instance);
        if (removed) {
            uniformsDirty = true;
        }
        return removed;
    }

    /**
     * Remove an instance by graphics reference.
     */
    public boolean removeByGraphics(Graphics graphics) {
        boolean removed = instances.removeIf(info -> info.getInstance() == graphics);
        if (removed) {
            uniformsDirty = true;
        }
        return removed;
    }

    /**
     * Clear all instances from this batch.
     */
    public void clearInstances() {
        instances.clear();
        visibleInstances = null;
        uniformsDirty = true;
    }

    /**
     * Check if this batch contains the given instance.
     */
    public boolean containsInstance(T instance) {
        return instances.contains(instance);
    }

    /**
     * Check if this batch is empty.
     */
    public boolean isEmpty() {
        return instances.isEmpty();
    }

    // ===== Visibility Management =====

    /**
     * Set the visible instances for current frame.
     * This is a subset of all instances that passed visibility tests.
     */
    public void setVisibleInstances(List<T> visible) {
        this.visibleInstances = visible;
        uniformsDirty = true;
    }

    /**
     * Mark all instances as visible.
     */
    public void markAllVisible() {
        this.visibleInstances = this.instances;
        uniformsDirty = true;
    }

    /**
     * Clear visible instances (prepare for new frame).
     */
    public void clearVisibleInstances() {
        if (this.visibleInstances != null && this.visibleInstances != this.instances) {
            this.visibleInstances.clear();
        } else {
            this.visibleInstances = new ArrayList<>();
        }
    }

    /**
     * Add a visible instance for current frame.
     */
    public void addVisibleInstance(T instance) {
        if (this.visibleInstances == null || this.visibleInstances == this.instances) {
            this.visibleInstances = new ArrayList<>();
        }
        this.visibleInstances.add(instance);
        uniformsDirty = true;
    }

    // ===== Uniform Batch Management =====

    /**
     * Mark uniforms as dirty, requiring recollection.
     */
    public void markUniformsDirty() {
        uniformsDirty = true;
    }

    /**
     * Update uniform batches for visible instances only.
     */
    public void updateUniformsForVisible() {
        if (uniformsDirty || visibleInstances != instances) {
            collectUniformBatchesForVisible();
            uniformsDirty = false;
        }
    }

    /**
     * Collect uniform batches for this render batch based on graphics instances
     */
    private void collectUniformBatches() {
        collectUniformBatchesInternal(instances);
        uniformsDirty = false;
    }

    /**
     * Collect uniform batches for visible instances only.
     */
    private void collectUniformBatchesForVisible() {
        List<T> targetInstances = visibleInstances != null ? visibleInstances : instances;
        collectUniformBatchesInternal(targetInstances);
    }

    /**
     * Internal method to collect uniform batches from a list of instances.
     */
    private void collectUniformBatchesInternal(List<T> targetInstances) {
        uniformBatches.clear();

        if (targetInstances.isEmpty()) {
            return;
        }

        // Get shader provider (cache it)
        if (cachedShaderProvider == null) {
            InstanceInfo firstInfo = targetInstances.get(0);
            cachedShaderProvider = extractShaderProvider(firstInfo);
        }

        if (cachedShaderProvider == null) {
            return;
        }

        UniformHookGroup hookGroup = cachedShaderProvider.getUniformHookGroup();

        // Build cached matching hooks if not already cached
        // We use the first instance's class to determine matching hooks
        // (all instances in a batch typically have the same class)
        if (cachedMatchingHooks == null && !targetInstances.isEmpty()) {
            Graphics firstInstance = targetInstances.get(0).getInstance();
            cachedMatchingHooks = hookGroup.getAllMatchingHooks(firstInstance.getClass());
        }

        // Collect uniform batches using optimized approach with cached hooks
        final Map<UniformValueSnapshot, UniformBatchGroup> batches = new HashMap<>();

        for (int i = 0; i < targetInstances.size(); i++) {
            InstanceInfo info = targetInstances.get(i);
            Graphics instance = info.getInstance();
            UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(hookGroup, instance, cachedMatchingHooks);
            batches.computeIfAbsent(snapshot, UniformBatchGroup::new).addInstance(instance);
        }

        uniformBatches.addAll(batches.values());
    }

    /**
     * Extract shader provider from InstanceInfo
     */
    protected ShaderProvider extractShaderProvider(InstanceInfo info) {
        try {
            ResourceReference<ShaderProvider> reference = ((ShaderState) info.getRenderSetting().renderState().get(ResourceTypes.SHADER_PROGRAM)).shader();
            if (reference.isAvailable()) {
                return reference.get();
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }

    /**
     * Invalidate cached shader provider (call when shader changes).
     */
    public void invalidateShaderCache() {
        cachedShaderProvider = null;
        cachedMatchingHooks = null;
        uniformsDirty = true;
    }
}