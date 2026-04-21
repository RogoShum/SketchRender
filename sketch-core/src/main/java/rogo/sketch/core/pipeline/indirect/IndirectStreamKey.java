package rogo.sketch.core.pipeline.indirect;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;

/**
 * Stable identity for a persistent indirect stream.
 */
public record IndirectStreamKey(
        KeyId stageId,
        PipelineType pipelineType,
        ExecutionKey stateKey,
        ResourceSetKey resourceSetKey,
        GeometryHandleKey geometryHandle,
        String tieBreaker
) {
    public IndirectStreamKey {
        tieBreaker = tieBreaker != null ? tieBreaker : "";
    }
}
