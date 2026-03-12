package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Post-processor that directly uploads VBO data and indirect command buffers.
 * <p>
 * Called either from the async render worker (if {@code allowUploadWorker()})
 * or from the main thread {@code SyncCommitPass} as a fallback.
 * No packet wrapping -- uploads are executed directly.
 */
public class RasterizationPostProcessor implements RenderPostProcessor {
    private final Map<VertexResource, VertexResourceManager.BuilderPair[]> uploadQueue = new HashMap<>();
    private final Set<IndirectCommandBuffer> indirectUploadQueue = new HashSet<>();

    public void add(VertexResource resource, VertexResourceManager.BuilderPair[] builders) {
        uploadQueue.computeIfAbsent(resource, k -> builders);
    }

    public void addIndirectUpload(IndirectCommandBuffer indirectCommandBuffer) {
        if (indirectCommandBuffer != null) {
            indirectUploadQueue.add(indirectCommandBuffer);
        }
    }

    @Override
    public void execute() {
        // Direct VBO uploads
        uploadQueue.forEach((resource, builders) -> {
            for (int i = 0; i < builders.length; ++i) {
                VertexResourceManager.BuilderPair pair = builders[i];
                resource.upload(pair.key(), pair.builder());
            }
        });

        // Direct indirect command buffer uploads
        for (IndirectCommandBuffer indirectCommandBuffer : indirectUploadQueue) {
            indirectCommandBuffer.bind();
            indirectCommandBuffer.upload();
        }
        if (!indirectUploadQueue.isEmpty()) {
            IndirectCommandBuffer.unBind();
        }

        uploadQueue.clear();
        indirectUploadQueue.clear();
    }
}
