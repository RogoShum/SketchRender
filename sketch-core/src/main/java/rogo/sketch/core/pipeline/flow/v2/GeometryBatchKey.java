package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.util.KeyId;

public record GeometryBatchKey(
        GeometrySourceKey geometrySourceKey,
        KeyId vertexLayoutKey,
        PrimitiveType primitiveType,
        SubmissionClass submissionClass
) {
    public GeometryBatchKey {
        geometrySourceKey = geometrySourceKey != null ? geometrySourceKey : GeometrySourceKey.empty();
        vertexLayoutKey = vertexLayoutKey != null ? vertexLayoutKey : KeyId.of("sketch:empty_vertex_layout");
        submissionClass = submissionClass != null ? submissionClass : SubmissionClass.DIRECT;
    }

    public static SubmissionClass submissionClassOf(SubmissionCapability capability) {
        if (capability != null && capability.supportsIndirect()) {
            return SubmissionClass.INDIRECT_CAPABLE;
        }
        return SubmissionClass.DIRECT;
    }

    public enum SubmissionClass {
        DIRECT,
        INDIRECT_CAPABLE
    }
}
