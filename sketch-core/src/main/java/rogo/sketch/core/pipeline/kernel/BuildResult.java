package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;

import java.util.List;
import java.util.Map;

/**
 * Cross-frame payload produced by async command build and consumed on next sync frame.
 * <p>
 * Contains the built render packets (directly as a map), the post-processors
 * that may still need main-thread execution, and a flag indicating whether
 * VBO uploads were already completed on the worker thread.
 * </p>
 *
 * @param frameNumber      the frame this was built for
 * @param postProcessors   post-processors that may need sync execution
 * @param uploadsCompleted true if the worker thread already performed VBO uploads
 */
public record BuildResult(
        long frameNumber,
        FrameExecutionPlan executionPlan,
        RenderPostProcessors postProcessors,
        boolean uploadsCompleted
) {
    public Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets() {
        return executionPlan != null ? executionPlan.stagePackets() : Map.of();
    }
}
