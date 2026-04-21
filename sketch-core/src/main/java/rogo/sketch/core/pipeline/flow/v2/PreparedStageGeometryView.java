package rogo.sketch.core.pipeline.flow.v2;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visibility-independent raster batch preparation that can be reused by the
 * frame-local finalize step.
 */
public final class PreparedStageGeometryView {
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final List<PreparedVisibleBatch> preparedBatches;
    private final Map<GeometryBatchKey, PreparedVisibleBatch> batchesByKey;

    public PreparedStageGeometryView(
            KeyId stageId,
            PipelineType pipelineType,
            List<PreparedVisibleBatch> preparedBatches) {
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.preparedBatches = preparedBatches != null ? List.copyOf(preparedBatches) : List.of();
        Map<GeometryBatchKey, PreparedVisibleBatch> indexed = new LinkedHashMap<>();
        for (PreparedVisibleBatch preparedBatch : this.preparedBatches) {
            if (preparedBatch != null) {
                indexed.put(preparedBatch.geometryBatchKey(), preparedBatch);
            }
        }
        this.batchesByKey = Map.copyOf(indexed);
    }

    public KeyId stageId() {
        return stageId;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public List<PreparedVisibleBatch> preparedBatches() {
        return preparedBatches;
    }

    public @Nullable PreparedVisibleBatch batch(GeometryBatchKey geometryBatchKey) {
        return batchesByKey.get(geometryBatchKey);
    }

    public boolean isEmpty() {
        return preparedBatches.isEmpty();
    }

    public record PreparedVisibleBatch(
            GeometryBatchKey geometryBatchKey,
            long firstPreparedOrder,
            RasterizationParameter rasterParameter,
            GeometryTraitsRef geometryTraits,
            VertexBufferKey vertexBufferKey,
            @Nullable BackendGeometryBinding sharedSourceGeometryBinding,
            List<StageEntityView.Entry> preparedEntries,
            List<PreparedCompiledSettingSlice> compiledSettingSlices
    ) {
        public PreparedVisibleBatch {
            preparedEntries = preparedEntries != null ? List.copyOf(preparedEntries) : List.of();
            compiledSettingSlices = compiledSettingSlices != null ? List.copyOf(compiledSettingSlices) : List.of();
        }
    }

    public record PreparedCompiledSettingSlice(
            CompiledRenderSetting compiledRenderSetting,
            List<StageEntityView.Entry> entries,
            List<PreparedMeshSlice> preparedMeshSlices,
            List<PreparedResourceGroupSlice> preparedResourceGroups
    ) {
        public PreparedCompiledSettingSlice {
            entries = entries != null ? List.copyOf(entries) : List.of();
            preparedMeshSlices = preparedMeshSlices != null ? List.copyOf(preparedMeshSlices) : List.of();
            preparedResourceGroups = preparedResourceGroups != null ? List.copyOf(preparedResourceGroups) : List.of();
        }
    }

    public record PreparedMeshSlice(
            @Nullable PreparedMesh preparedMesh,
            List<StageEntityView.Entry> entries
    ) {
        public PreparedMeshSlice {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }
    }

    public record PreparedResourceGroupSlice(
            Object sourceSlice,
            ExecutionKey stateKey,
            ResourceBindingPlan bindingPlan,
            ResourceSetKey resourceSetKey,
            UniformGroupSet uniformGroups,
            List<StageEntityView.Entry> preparedEntries
    ) {
        public PreparedResourceGroupSlice {
            preparedEntries = preparedEntries != null ? List.copyOf(preparedEntries) : List.of();
            uniformGroups = uniformGroups != null ? uniformGroups : UniformGroupSet.empty();
            resourceSetKey = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
        }
    }
}
