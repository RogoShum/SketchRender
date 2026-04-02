package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy uniform regroup helper retained only while resource/draw frequency
 * sets are migrating to the V2 packet compiler path.
 */
@Deprecated(forRemoval = false)
public class UniformBatchGroup {
    private final UniformValueSnapshot uniformSnapshot;
    private final UniformGroupSet uniformGroups;
    private final List<Graphics> instances;

    public UniformBatchGroup(UniformValueSnapshot uniformSnapshot) {
        this.uniformSnapshot = uniformSnapshot;
        this.uniformGroups = UniformGroupSet.fromLegacy(uniformSnapshot);
        this.instances = new ArrayList<>();
    }

    public void addInstance(Graphics instance) {
        instances.add(instance);
    }

    public UniformValueSnapshot getUniformSnapshot() {
        return uniformSnapshot;
    }

    public UniformGroupSet getUniformGroups() {
        return uniformGroups;
    }

    public ResourceSetKey resourceSetKey(ResourceBindingPlan bindingPlan) {
        return ResourceSetKey.from(bindingPlan, uniformGroups.resourceUniforms());
    }

    public List<Graphics> getInstances() {
        return instances;
    }

    public int size() {
        return instances.size();
    }

    public boolean isEmpty() {
        return instances.isEmpty();
    }

    public void clear() {
        instances.clear();
    }

    @Override
    public String toString() {
        return "UniformBatchGroup{" +
                "uniformSnapshot=" + uniformSnapshot +
                ", instanceCount=" + instances.size() +
                '}';
    }
}
