package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;

import java.util.List;
import java.util.Map;

/**
 * Cross-frame payload produced by async command build and consumed on next sync frame.
 * <p>
 * Contains the built render commands (directly as a map), the post-processors
 * that may still need main-thread execution, and a flag indicating whether
 * VBO uploads were already completed on the worker thread.
 * </p>
 *
 * @param frameNumber      the frame this was built for
 * @param commands         the built render commands grouped by pipeline type and render setting
 * @param postProcessors   post-processors that may need sync execution
 * @param uploadsCompleted true if the worker thread already performed VBO uploads
 */
public record BuildResult(
        long frameNumber,
        Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> commands,
        RenderPostProcessors postProcessors,
        boolean uploadsCompleted
) {
}
