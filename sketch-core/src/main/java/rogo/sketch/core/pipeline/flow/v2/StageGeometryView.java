package rogo.sketch.core.pipeline.flow.v2;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.util.KeyId;

import java.util.List;

/**
 * Immutable frame-local view of stage-visible raster batches.
 */
public final class StageGeometryView {
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final long visibilityRevision;
    private final List<VisibleBatch> visibleBatches;

    public StageGeometryView(
            KeyId stageId,
            PipelineType pipelineType,
            long visibilityRevision,
            List<VisibleBatch> visibleBatches) {
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.visibilityRevision = visibilityRevision;
        this.visibleBatches = visibleBatches != null ? List.copyOf(visibleBatches) : List.of();
    }

    public KeyId stageId() {
        return stageId;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public long visibilityRevision() {
        return visibilityRevision;
    }

    public List<VisibleBatch> visibleBatches() {
        return visibleBatches;
    }

    public boolean isEmpty() {
        return visibleBatches.isEmpty();
    }

    public record VisibleBatch(
            VisibleInstanceSlice sourceSlice,
            GeometryBatchKey geometryBatchKey,
            long firstVisibleOrder,
            RasterizationParameter rasterParameter,
            GeometryTraitsRef geometryTraits,
            VertexBufferKey vertexBufferKey,
            @Nullable BackendGeometryBinding installedGeometryBinding,
            @Nullable BackendGeometryBinding sharedSourceGeometryBinding,
            List<StageEntityView.Entry> visibleEntries,
            List<CompiledSettingSlice> compiledSettingSlices
    ) {
        public VisibleBatch {
            visibleEntries = visibleEntries != null ? List.copyOf(visibleEntries) : List.of();
            compiledSettingSlices = compiledSettingSlices != null ? List.copyOf(compiledSettingSlices) : List.of();
        }
    }

    public record CompiledSettingSlice(
            CompiledRenderSetting compiledRenderSetting,
            List<StageEntityView.Entry> entries,
            List<PreparedMeshSlice> preparedMeshSlices,
            List<ResourceGroupSlice> resourceGroups
    ) {
        public CompiledSettingSlice {
            entries = entries != null ? List.copyOf(entries) : List.of();
            preparedMeshSlices = preparedMeshSlices != null ? List.copyOf(preparedMeshSlices) : List.of();
            resourceGroups = resourceGroups != null ? List.copyOf(resourceGroups) : List.of();
        }
    }

    public record PreparedMeshSlice(
            @Nullable rogo.sketch.core.api.model.PreparedMesh preparedMesh,
            List<StageEntityView.Entry> entries
    ) {
        public PreparedMeshSlice {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }
    }
}
