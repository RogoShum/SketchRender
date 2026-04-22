package rogo.sketch.core.pipeline;

import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.util.KeyId;

@FunctionalInterface
interface PipelineFlowSceneFactory {
    StageFlowScene<?> create(
            KeyId stageId,
            PipelineType pipelineType,
            GraphicsPipeline<?> pipeline,
            FrameDataDomain dataDomain,
            RenderTraceRecorder renderTraceRecorder);
}
