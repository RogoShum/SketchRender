package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-processor that captures backend-neutral geometry/resource upload plans
 * and delegates installed-resource uploads to the active backend path.
 * <p>
 * Called either from the async render worker (if {@code allowUploadWorker()})
 * or from the main thread {@code SyncCommitPass} as a fallback.
 * No packet wrapping -- uploads are executed directly.
 */
public class RasterizationPostProcessor implements RenderPostProcessor {
    private final Map<GeometryHandleKey, FrameExecutionPlan.GeometryUploadPlan> geometryUploadPlans = new LinkedHashMap<>();
    private final Map<ResourceSetKey, FrameExecutionPlan.ResourceUploadPlan> resourceUploadPlans = new LinkedHashMap<>();

    public void addGeometryUpload(
            GeometryHandleKey geometryHandle,
            KeyId vertexLayoutKey,
            SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot,
            BackendGeometryBinding targetGeometryBinding,
            BackendGeometryBinding sourceGeometryBinding,
            GeometryResourceCoordinator.BuilderPair[] builders,
            BackendInstalledBuffer indirectBuffer,
            int vertexCount,
            int indexCount) {
        if (geometryHandle == null) {
            return;
        }
        geometryUploadPlans.put(geometryHandle, FrameExecutionPlan.GeometryUploadPlan.capture(
                geometryHandle,
                vertexLayoutKey,
                sharedGeometrySourceSnapshot,
                targetGeometryBinding,
                sourceGeometryBinding,
                builders,
                indirectBuffer,
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
            try {
                geometryUploadPlan.uploadTo(geometryUploadPlan.targetGeometryBinding());
                geometryUploadPlan.uploadIndirect();
            } finally {
                geometryUploadPlan.releaseBuilderSnapshots();
            }
        }

        geometryUploadPlans.clear();
        resourceUploadPlans.clear();
    }
}

