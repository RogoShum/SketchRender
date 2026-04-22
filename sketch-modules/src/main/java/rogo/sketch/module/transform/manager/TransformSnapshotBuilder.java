package rogo.sketch.module.transform.manager;

import rogo.sketch.module.transform.TransformData;

import java.util.List;

/**
 * Builds publishable CPU-side transform snapshots from resolved hierarchy and state.
 */
public class TransformSnapshotBuilder {
    private final TransformPreparedTickSnapshot[] tickSnapshots = {
            new TransformPreparedTickSnapshot(64, 64),
            new TransformPreparedTickSnapshot(64, 64)
    };
    private int nextSnapshotIndex = 0;

    public TransformPreparedTickSnapshot buildTickSnapshot(
            long logicTickEpoch,
            TransformRegistry registry,
            TransformHierarchyGraph hierarchyGraph
    ) {
        hierarchyGraph.resolveIfNeeded(registry);

        TransformPreparedTickSnapshot snapshot = tickSnapshots[nextSnapshotIndex];
        nextSnapshotIndex = (nextSnapshotIndex + 1) % tickSnapshots.length;

        snapshot.setLogicTickEpoch(logicTickEpoch);
        buildPipelineSnapshot(snapshot.syncSnapshot(), hierarchyGraph.syncLayers(), hierarchyGraph.syncMaxDepth());
        buildPipelineSnapshot(snapshot.asyncSnapshot(), hierarchyGraph.asyncLayers(), hierarchyGraph.asyncMaxDepth());
        return snapshot;
    }

    public void cleanup() {
        for (TransformPreparedTickSnapshot snapshot : tickSnapshots) {
            snapshot.cleanup();
        }
    }

    private void buildPipelineSnapshot(
            TransformUploadSnapshot snapshot,
            List<List<TransformBinding>> layers,
            int maxDepth
    ) {
        int activeCount = 0;
        for (List<TransformBinding> layer : layers) {
            activeCount += layer.size();
        }

        snapshot.beginBuild(activeCount, maxDepth);
        if (activeCount == 0) {
            return;
        }

        long inputPtr = snapshot.inputBuilder().getBaseAddress();
        int flattenedOffset = 0;
        for (int depth = 0; depth <= maxDepth; depth++) {
            List<TransformBinding> layer = depth < layers.size() ? layers.get(depth) : List.of();
            snapshot.addDispatchRange(flattenedOffset, layer.size());

            for (TransformBinding binding : layer) {
                snapshot.putBindingOffset(binding.transformId(), flattenedOffset);
                int flags = TransformData.computeFlags(
                        binding.currentTickData(),
                        binding.pendingTickData(),
                        binding.parentTransformId());

                TransformData.writeToBuffer(
                        binding.currentTickData(),
                        binding.pendingTickData(),
                        inputPtr,
                        binding.parentTransformId(),
                        binding.transformId(),
                        flags);

                inputPtr += TransformUploadSnapshot.INPUT_STRIDE;
                flattenedOffset++;
            }
        }

        snapshot.inputBuilder().setWriteOffset((long) activeCount * TransformUploadSnapshot.INPUT_STRIDE);
    }
}
