package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.Map;

public class RasterizationPostProcessor implements RenderPostProcessor {
    private final Map<VertexResource, Map<KeyId, VertexStreamBuilder>> uploadQueue = new HashMap<>();

    public void add(VertexResource resource, Map<KeyId, VertexStreamBuilder> builders) {
        uploadQueue.computeIfAbsent(resource, k -> new HashMap<>()).putAll(builders);
    }

    @Override
    public void execute() {
        uploadQueue.forEach((resource, builders) -> {
            builders.forEach((component, builder) -> {
                resource.upload(component, builder);
            });
        });
        uploadQueue.clear();
    }
}