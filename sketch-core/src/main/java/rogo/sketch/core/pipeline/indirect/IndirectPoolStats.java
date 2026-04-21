package rogo.sketch.core.pipeline.indirect;

public record IndirectPoolStats(
        int laneCount,
        int streamCount,
        int freeSliceCount
) {
}
