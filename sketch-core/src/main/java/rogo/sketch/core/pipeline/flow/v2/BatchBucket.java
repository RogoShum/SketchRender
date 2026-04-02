package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.flow.MeshRenderBatch;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.pipeline.information.InstanceInfo;

public record BatchBucket<I extends InstanceInfo<?>>(
        RenderBatch<I> legacyBatch,
        GeometryBatchKey geometryBatchKey
) {
    public static <I extends InstanceInfo<?>> BatchBucket<I> from(RenderBatch<I> batch) {
        GeometrySourceKey sourceKey = GeometrySourceKey.empty();
        if (batch instanceof MeshRenderBatch meshRenderBatch) {
            sourceKey = GeometrySourceKey.fromPreparedMesh(meshRenderBatch.mesh());
        } else if (!batch.getInstances().isEmpty() && batch.getInstances().get(0) instanceof rogo.sketch.core.pipeline.information.RasterizationInstanceInfo rasterInfo) {
            sourceKey = GeometrySourceKey.fromPreparedMesh(rasterInfo.getMesh());
        }

        var compiledSetting = RenderSettingCompiler.compile(batch.getRenderSetting());
        SubmissionCapability submissionCapability = !batch.getInstances().isEmpty()
                ? batch.getInstances().get(0).getInstance().submissionCapability()
                : SubmissionCapability.DIRECT_BATCHABLE;
        return new BatchBucket<>(
                batch,
                new GeometryBatchKey(
                        sourceKey,
                        compiledSetting.pipelineStateDescriptor().vertexLayoutKey(),
                        batch.getRenderSetting().renderParameter() != null
                                ? batch.getRenderSetting().renderParameter().primitiveType()
                                : null,
                        GeometryBatchKey.submissionClassOf(submissionCapability)));
    }
}
