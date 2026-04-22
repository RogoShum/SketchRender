package rogo.sketch.core.packet;

import rogo.sketch.core.driver.state.CompiledComputeState;
import rogo.sketch.core.driver.state.ComputeStateCompiler;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.pipeline.ComputeRenderSetting;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public final class ComputePipelineKey implements ExecutionKey {
    private static final KeyId UNBOUND_SHADER = KeyId.of("sketch:unbound_shader");

    private final RenderStatePatch renderState;
    private final CompiledComputeState compiledComputeState;
    private final ResourceBindingPlan bindingPlan;
    private final boolean shouldSwitchRenderState;
    private final KeyId shaderId;
    private final ShaderVariantKey shaderVariantKey;
    private final KeyId resourceLayoutKey;
    private final int resourceBindingSignature;
    private final int hashCode;

    public ComputePipelineKey(
            RenderStatePatch renderState,
            CompiledComputeState compiledComputeState,
            ResourceBindingPlan bindingPlan,
            boolean shouldSwitchRenderState,
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey,
            KeyId resourceLayoutKey) {
        this.renderState = renderState != null ? renderState : RenderStatePatch.empty();
        this.compiledComputeState = compiledComputeState != null
                ? compiledComputeState
                : ComputeStateCompiler.compile(this.renderState);
        this.bindingPlan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.shaderId = shaderId != null ? shaderId : UNBOUND_SHADER;
        this.shaderVariantKey = shaderVariantKey != null ? shaderVariantKey : ShaderVariantKey.EMPTY;
        this.resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : ResourceBindingPlan.empty().layoutKey();
        this.resourceBindingSignature = this.bindingPlan.resourceBindingHash();
        this.hashCode = Objects.hash(
                ExecutionDomain.COMPUTE,
                this.shouldSwitchRenderState,
                this.shaderId,
                this.shaderVariantKey,
                this.resourceLayoutKey,
                this.resourceBindingSignature);
    }

    public static ComputePipelineKey from(ComputeRenderSetting renderSetting) {
        Objects.requireNonNull(renderSetting, "renderSetting");
        ShaderState shaderState = null;
        if (renderSetting.renderState() != null
                && renderSetting.renderState().get(ShaderState.TYPE) instanceof ShaderState state) {
            shaderState = state;
        }
        ResourceBindingPlan bindingPlan = ResourceBindingPlan.from(renderSetting.resourceBinding());
        return new ComputePipelineKey(
                renderSetting.renderState(),
                ComputeStateCompiler.compile(renderSetting.renderState()),
                bindingPlan,
                renderSetting.shouldSwitchRenderState(),
                shaderState != null ? shaderState.getShaderId() : UNBOUND_SHADER,
                shaderState != null ? shaderState.getVariantKey() : ShaderVariantKey.EMPTY,
                bindingPlan.layoutKey());
    }

    @Override
    public ExecutionDomain domain() {
        return ExecutionDomain.COMPUTE;
    }

    public RenderStatePatch renderState() {
        return renderState;
    }

    public CompiledComputeState compiledComputeState() {
        return compiledComputeState;
    }

    @Override
    public ResourceBindingPlan bindingPlan() {
        return bindingPlan;
    }

    @Override
    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    @Override
    public KeyId shaderId() {
        return shaderId;
    }

    @Override
    public ShaderVariantKey shaderVariantKey() {
        return shaderVariantKey;
    }

    @Override
    public KeyId resourceLayoutKey() {
        return resourceLayoutKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ComputePipelineKey other)) {
            return false;
        }
        return shouldSwitchRenderState == other.shouldSwitchRenderState
                && Objects.equals(shaderId, other.shaderId)
                && Objects.equals(shaderVariantKey, other.shaderVariantKey)
                && Objects.equals(resourceLayoutKey, other.resourceLayoutKey)
                && resourceBindingSignature == other.resourceBindingSignature;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
