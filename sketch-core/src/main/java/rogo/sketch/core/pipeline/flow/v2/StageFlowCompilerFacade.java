package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.Map;

public final class StageFlowCompilerFacade {
    private final GeometryBatchProcessor legacyProcessor;

    public StageFlowCompilerFacade(GeometryBatchProcessor legacyProcessor) {
        this.legacyProcessor = legacyProcessor;
    }

    public Map<PipelineStateKey, List<RenderPacket>> compilePackets(
            BatchContainer<?, ?> batchContainer,
            PipelineType pipelineType,
            RenderFlowType flowType,
            KeyId stageId,
            RenderPostProcessors postProcessors,
            RenderContext context) {
        return legacyProcessor.createPacketsUnchecked(
                batchContainer,
                pipelineType,
                flowType,
                stageId,
                postProcessors,
                context);
    }

    @Deprecated
    public Map<RenderSetting, List<RenderCommand>> compileLegacyCommands(
            BatchContainer<?, ?> batchContainer,
            RenderFlowType flowType,
            KeyId stageId,
            RenderPostProcessors postProcessors,
            RenderContext context) {
        return legacyProcessor.createLegacyCommandsUnchecked(batchContainer, flowType, stageId, postProcessors, context);
    }
}
