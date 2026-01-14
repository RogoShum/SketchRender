package rogo.sketch.render.pipeline;

import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.information.InstanceInfo;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.uniform.UniformValueSnapshot;
import rogo.sketch.render.state.gl.ShaderState;

import java.util.*;
import java.util.stream.Collectors;

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
     * Organize instances into batches based on RenderSetting.
     */
    public static <T extends InstanceInfo> List<RenderBatch<T>> organize(Collection<T> allData) {
        if (allData.isEmpty())
            return List.of();

        // Group by RenderSetting
        Map<RenderSetting, List<T>> grouped = allData.stream()
                .collect(Collectors.groupingBy(InstanceInfo::getRenderSetting));

        List<RenderBatch<T>> batches = new ArrayList<>();
        for (Map.Entry<RenderSetting, List<T>> entry : grouped.entrySet()) {
            batches.add(new RenderBatch<>(entry.getKey(), entry.getValue()));
        }

        return batches;
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
        Map<UniformValueSnapshot, UniformBatchGroup> batches = new HashMap<>();

        for (InstanceInfo info : instances) {
            Graphics instance = info.getInstance();
            if (instance.shouldRender()) {
                UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(
                        shaderProvider.getUniformHookGroup(), instance);

                batches.computeIfAbsent(snapshot, UniformBatchGroup::new).addInstance(instance);
            }
        }

        this.uniformBatches.clear();
        this.uniformBatches.addAll(batches.values());
    }

    /**
     * Extract shader provider from InstanceInfo
     */
    private ShaderProvider extractShaderProvider(InstanceInfo info) {
        try {
            ResourceReference<ShaderProvider> reference = ((ShaderState) info.getRenderSetting().renderState()
                    .get(ResourceTypes.SHADER_PROGRAM)).shader();
            if (reference.isAvailable()) {
                return reference.get();
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }
}
