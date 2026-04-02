package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.FullRenderState;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

public record PipelineStateDescriptor(
        RenderParameter renderParameter,
        FullRenderState renderState,
        boolean shouldSwitchRenderState,
        KeyId shaderId,
        ShaderVariantKey shaderVariantKey,
        KeyId vertexLayoutKey,
        KeyId targetLayoutKey,
        KeyId resourceLayoutKey,
        int rasterStateSignature
) {
    public PipelineStateDescriptor {
        shaderId = shaderId != null ? shaderId : KeyId.of("sketch:unbound_shader");
        shaderVariantKey = shaderVariantKey != null ? shaderVariantKey : ShaderVariantKey.EMPTY;
        vertexLayoutKey = vertexLayoutKey != null ? vertexLayoutKey : KeyId.of("sketch:empty_vertex_layout");
        targetLayoutKey = targetLayoutKey != null ? targetLayoutKey : KeyId.of("sketch:default_render_target");
        resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout");
    }

    public PipelineStateKey toStateKey(ResourceBindingPlan bindingPlan) {
        return new PipelineStateKey(
                renderParameter,
                renderState,
                bindingPlan,
                shouldSwitchRenderState,
                shaderId,
                shaderVariantKey,
                vertexLayoutKey,
                targetLayoutKey,
                resourceLayoutKey);
    }
}
