package rogo.sketch.core.pipeline;

import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.ResourceBindingPlan;

public record CompiledRenderSetting(
        RenderSetting renderSetting,
        PipelineStateDescriptor pipelineStateDescriptor,
        ResourceBindingDescriptor resourceBindingDescriptor,
        TargetBindingDescriptor targetBindingDescriptor,
        ResourceBindingPlan resourceBindingPlan,
        ExecutionKey pipelineStateKey
) {
}
