package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.driver.state.CompiledRenderState;
import rogo.sketch.core.driver.state.RenderStateCompiler;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

public final class RenderSettingCompiler {
    private static final KeyId UNBOUND_SHADER = KeyId.of("sketch:unbound_shader");
    private static final KeyId EMPTY_VERTEX_LAYOUT = KeyId.of("sketch:empty_vertex_layout");

    private RenderSettingCompiler() {
    }

    public static CompiledRenderSetting compile(RenderSetting renderSetting) {
        RenderSetting setting = renderSetting != null
                ? renderSetting
                : RenderSetting.fromPartial(null, PartialRenderSetting.EMPTY);
        ResourceBindingPlan bindingPlan = ResourceBindingPlan.from(setting.resourceBinding());
        TargetBindingDescriptor targetBindingDescriptor = TargetBindingDescriptor.from(setting.targetBinding());
        CompiledRenderState compiledRenderState = RenderStateCompiler.compile(setting.renderState());

        ShaderState shaderState = null;
        if (setting.renderState() != null && setting.renderState().get(ShaderState.TYPE) instanceof ShaderState state) {
            shaderState = state;
        }

        PipelineStateDescriptor pipelineDescriptor = new PipelineStateDescriptor(
                setting.renderParameter(),
                setting.renderState(),
                compiledRenderState,
                setting.shouldSwitchRenderState(),
                shaderState != null ? shaderState.getShaderId() : UNBOUND_SHADER,
                shaderState != null ? shaderState.getVariantKey() : ShaderVariantKey.EMPTY,
                setting.renderParameter() != null && setting.renderParameter().getLayout() != null
                        ? KeyId.of("sketch:vertex_layout_" + Integer.toHexString(setting.renderParameter().getLayout().hashCode()))
                        : EMPTY_VERTEX_LAYOUT,
                targetBindingDescriptor.passCompatibilityKey(),
                bindingPlan.layoutKey(),
                compiledRenderState.pipelineRasterState() != null ? compiledRenderState.pipelineRasterState().hashCode() : 0);

        PipelineStateKey stateKey = pipelineDescriptor.toStateKey(bindingPlan);
        return new CompiledRenderSetting(
                setting,
                pipelineDescriptor,
                ResourceBindingDescriptor.from(bindingPlan),
                targetBindingDescriptor,
                bindingPlan,
                stateKey);
    }
}

