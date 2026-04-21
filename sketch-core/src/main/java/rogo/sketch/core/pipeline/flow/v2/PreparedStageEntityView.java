package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints;
import rogo.sketch.core.pipeline.flow.ecs.SpatialIndexSystem;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

import static rogo.sketch.core.pipeline.PipelineType.RASTERIZATION;
import static rogo.sketch.core.pipeline.PipelineType.TRANSLUCENT;

/**
 * Tick-stable stage snapshot prepared ahead of frame-local culling/finalization.
 */
public final class PreparedStageEntityView {
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final List<ContainerSlice> orderedContainers;
    private final StageEntityView fullView;

    public PreparedStageEntityView(
            KeyId stageId,
            PipelineType pipelineType,
            List<ContainerSlice> orderedContainers
    ) {
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.orderedContainers = orderedContainers != null ? List.copyOf(orderedContainers) : List.of();

        List<StageEntityView.Entry> orderedEntries = new ArrayList<>();
        for (ContainerSlice slice : this.orderedContainers) {
            if (slice == null || slice.entries().isEmpty()) {
                continue;
            }
            orderedEntries.addAll(slice.entries());
        }
        this.fullView = new StageEntityView(stageId, pipelineType, orderedEntries);
    }

    public KeyId stageId() {
        return stageId;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public List<ContainerSlice> orderedContainers() {
        return orderedContainers;
    }

    public StageEntityView fullView() {
        return fullView;
    }

    public boolean isEmpty() {
        return fullView.isEmpty();
    }

    public <C extends RenderContext> StageEntityView finalizeForFrame(
            SpatialIndexSystem<C> spatialIndexSystem,
            C context
    ) {
        if (fullView.isEmpty()) {
            return fullView;
        }
        if (pipelineType != RASTERIZATION && pipelineType != TRANSLUCENT) {
            return fullView;
        }

        List<StageEntityView.Entry> finalizedEntries = new ArrayList<>(fullView.entries().size());
        for (ContainerSlice slice : orderedContainers) {
            if (slice == null || slice.entries().isEmpty()) {
                continue;
            }
            List<StageEntityView.Entry> entries = slice.entries();
            if (GraphicsContainerHints.AABB_TREE.equals(slice.containerType())
                    || GraphicsContainerHints.OCTREE.equals(slice.containerType())) {
                entries = spatialIndexSystem.collectVisible(entries, context);
            }
            finalizedEntries.addAll(entries);
        }
        return new StageEntityView(stageId, pipelineType, finalizedEntries);
    }

    public record ContainerSlice(
            KeyId containerType,
            List<StageEntityView.Entry> entries
    ) {
        public ContainerSlice {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }
    }
}
