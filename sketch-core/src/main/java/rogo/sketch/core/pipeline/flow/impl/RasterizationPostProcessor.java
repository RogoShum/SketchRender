package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<GeometryHandleKey, FrameExecutionPlan.GeometryUploadPlan> geometryUploadPlans = new LinkedHashMap<>();
    private final Map<ResourceSetKey, FrameExecutionPlan.ResourceUploadPlan> resourceUploadPlans = new LinkedHashMap<>();

    public void add(VertexResource resource, VertexResourceManager.BuilderPair[] builders) {
        uploadQueue.computeIfAbsent(resource, k -> VertexResourceManager.snapshotBuilders(builders));
    }

    public void addIndirectUpload(IndirectCommandBuffer indirectCommandBuffer) {
        if (indirectCommandBuffer != null) {
            indirectUploadQueue.add(indirectCommandBuffer);
        }
    }

    public void addGeometryUpload(
            GeometryHandleKey geometryHandle,
            KeyId vertexLayoutKey,
            VertexResource resource,
            VertexResourceManager.BuilderPair[] builders,
            IndirectCommandBuffer indirectCommandBuffer,
            int vertexCount,
            int indexCount) {
        if (geometryHandle == null || resource == null) {
            return;
        }
        geometryUploadPlans.put(geometryHandle, new FrameExecutionPlan.GeometryUploadPlan(
                geometryHandle,
                vertexLayoutKey,
                resource,
                builders,
                indirectCommandBuffer,
                vertexCount,
                indexCount));
    }

    public void addResourceUpload(
            KeyId stageId,
            ResourceSetKey resourceSetKey,
            ResourceBindingPlan bindingPlan,
            UniformGroupSet uniformGroups,
            KeyId shaderId) {
        if (resourceSetKey == null || resourceSetKey.isEmpty()) {
            return;
        }
        resourceUploadPlans.put(resourceSetKey, new FrameExecutionPlan.ResourceUploadPlan(
                stageId,
                resourceSetKey,
                bindingPlan,
                uniformGroups,
                shaderId,
                resourceSetKey.resourceLayoutKey()));
    }

    public List<FrameExecutionPlan.GeometryUploadPlan> geometryUploadPlans() {
        return List.copyOf(geometryUploadPlans.values());
    }

    public List<FrameExecutionPlan.ResourceUploadPlan> resourceUploadPlans() {
        return new ArrayList<>(resourceUploadPlans.values());
    }

    @Override
    public void execute() {
        for (FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan : geometryUploadPlans.values()) {
            geometryUploadPlan.apply();
        }

        // Direct VBO uploads
        uploadQueue.forEach((resource, builders) -> {
            try {
                for (int i = 0; i < builders.length; ++i) {
                    VertexResourceManager.BuilderPair pair = builders[i];
                    if (pair == null || pair.builder() == null) {
                        continue;
                    }
                    resource.upload(pair.key(), pair.builder());
                }
            } finally {
                for (VertexResourceManager.BuilderPair pair : builders) {
                    if (pair != null && pair.builder() != null) {
                        pair.builder().close();
                    }
                }
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

        geometryUploadPlans.clear();
        resourceUploadPlans.clear();
        uploadQueue.clear();
        indirectUploadQueue.clear();
    }
}
