package rogo.sketch.core.pipeline;

import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.ResourceBindingPlan;

public record CompiledRenderSetting(
        RenderSetting renderSetting,
        PipelineStateDescriptor pipelineStateDescriptor,
        ResourceBindingDescriptor resourceBindingDescriptor,
        TargetBindingDescriptor targetBindingDescriptor,
        ResourceBindingPlan resourceBindingPlan,
        PipelineStateKey pipelineStateKey
) {
}
