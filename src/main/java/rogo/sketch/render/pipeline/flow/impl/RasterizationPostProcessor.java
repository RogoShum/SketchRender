package rogo.sketch.render.pipeline.flow.impl;

import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.render.pipeline.flow.RenderPostProcessor;
import rogo.sketch.render.resource.buffer.VertexResource;

import java.util.HashMap;
import java.util.Map;

public class RasterizationPostProcessor implements RenderPostProcessor {
    private final Map<VertexResource, Map<Integer, VertexDataBuilder>> uploadQueue = new HashMap<>();

    public void add(VertexResource resource, Map<Integer, VertexDataBuilder> builders) {
        uploadQueue.computeIfAbsent(resource, k -> new HashMap<>()).putAll(builders);
    }

    @Override
    public void execute() {
        uploadQueue.forEach((resource, builders) -> {
            builders.forEach((binding, builder) -> {
                resource.upload(binding, builder);
            });
        });
        uploadQueue.clear();
    }
}