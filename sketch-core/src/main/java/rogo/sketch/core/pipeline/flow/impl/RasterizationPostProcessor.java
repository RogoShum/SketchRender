package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.HashMap;
import java.util.Map;

public class RasterizationPostProcessor implements RenderPostProcessor {
    private final Map<VertexResource, VertexResourceManager.BuilderPair[]> uploadQueue = new HashMap<>();

    public void add(VertexResource resource, VertexResourceManager.BuilderPair[] builders) {
        uploadQueue.computeIfAbsent(resource, k -> builders);
    }

    @Override
    public void execute() {
        uploadQueue.forEach((resource, builders) -> {
            for (int i = 0; i < builders.length; ++i) {
                VertexResourceManager.BuilderPair pair = builders[i];
                resource.upload(pair.key(), pair.builder());
            }
        });
        uploadQueue.clear();
    }
}