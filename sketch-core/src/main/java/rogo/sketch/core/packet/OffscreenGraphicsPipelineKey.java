package rogo.sketch.core.packet;

import rogo.sketch.core.driver.state.CompiledRasterState;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

public final class OffscreenGraphicsPipelineKey extends RasterPipelineKey {
    public OffscreenGraphicsPipelineKey(
            RenderParameter renderParameter,
            RenderStatePatch renderState,
            CompiledRasterState compiledRasterState,
            ResourceBindingPlan bindingPlan,
            boolean shouldSwitchRenderState,
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey,
            KeyId vertexLayoutKey,
            KeyId renderTargetKey,
            KeyId resourceLayoutKey) {
        super(
                ExecutionDomain.OFFSCREEN_GRAPHICS,
                renderParameter,
                renderState,
                compiledRasterState,
                bindingPlan,
                shouldSwitchRenderState,
                shaderId,
                shaderVariantKey,
                vertexLayoutKey,
                renderTargetKey,
                resourceLayoutKey);
    }
}
