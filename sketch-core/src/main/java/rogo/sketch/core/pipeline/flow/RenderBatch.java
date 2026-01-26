package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.UniformBatchGroup;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.state.gl.ShaderState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a batch of graphics instances that share the same render settings.
 *
 * @param <T> The type of instance info contained in this batch
 */
public class RenderBatch<T extends InstanceInfo> {
    private final RenderSetting renderSetting;
    private final List<T> instances;
    private final List<UniformBatchGroup> uniformBatches;

    public RenderBatch(RenderSetting renderSetting, List<T> instances) {
        this.renderSetting = renderSetting;
        this.instances = instances;
        this.uniformBatches = new ArrayList<>();
        collectUniformBatches();
    }

    public RenderSetting getRenderSetting() {
        return renderSetting;
    }

    public List<T> getInstances() {
        return instances;
    }

    public int getInstanceCount() {
        return instances.size();
    }

    public List<UniformBatchGroup> getUniformBatches() {
        return new ArrayList<>(uniformBatches);
    }

    /**
     * Collect uniform batches for this render batch based on graphics instances
     */
    private void collectUniformBatches() {
        if (instances.isEmpty()) {
            return;
        }

        // Get render setting and shader provider from the first instance
        InstanceInfo firstInfo = instances.get(0);
        ShaderProvider shaderProvider = extractShaderProvider(firstInfo);

        if (shaderProvider == null) {
            return;
        }

        // Collect uniform batches
        final Map<UniformValueSnapshot, UniformBatchGroup> batches = new HashMap<>();
        for (InstanceInfo info : instances) {
            Graphics instance = info.getInstance();
            UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(shaderProvider.getUniformHookGroup(), instance);
            batches.computeIfAbsent(snapshot, UniformBatchGroup::new).addInstance(instance);
        }

        this.uniformBatches.clear();
        this.uniformBatches.addAll(batches.values());
    }

    /**
     * Extract shader provider from InstanceInfo
     */
    private ShaderProvider extractShaderProvider(InstanceInfo info) {
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
}
