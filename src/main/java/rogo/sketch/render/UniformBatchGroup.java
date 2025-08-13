package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.render.uniform.UniformValueSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UniformBatchGroup {
    private final UniformValueSnapshot uniformSnapshot;
    private final List<GraphicsInstance> instances;

    public UniformBatchGroup(UniformValueSnapshot uniformSnapshot) {
        this.uniformSnapshot = uniformSnapshot;
        this.instances = new ArrayList<>();
    }

    public void addInstance(GraphicsInstance instance) {
        instances.add(instance);
    }

    public UniformValueSnapshot getUniformSnapshot() {
        return uniformSnapshot;
    }

    public List<GraphicsInstance> getInstances() {
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