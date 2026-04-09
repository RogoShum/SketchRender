package rogo.sketch.core.packet;

import rogo.sketch.core.driver.state.CompiledRenderState;
import rogo.sketch.core.driver.state.RenderStateCompiler;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public final class PipelineStateKey {
    private static final KeyId UNBOUND_SHADER = KeyId.of("sketch:unbound_shader");
    private static final KeyId EMPTY_VERTEX_LAYOUT = KeyId.of("sketch:empty_vertex_layout");
    private static final KeyId DEFAULT_RENDER_TARGET = KeyId.of("sketch:default_render_target");

    private final RenderParameter renderParameter;
    private final RenderStatePatch renderState;
    private final CompiledRenderState compiledRenderState;
    private final ResourceBindingPlan bindingPlan;
    private final boolean shouldSwitchRenderState;
    private final KeyId shaderId;
    private final ShaderVariantKey shaderVariantKey;
    private final KeyId vertexLayoutKey;
    private final KeyId renderTargetKey;
    private final KeyId resourceLayoutKey;
    private final int rasterStateSignature;
    private final int resourceBindingSignature;
    private final int hashCode;

    public PipelineStateKey(
            RenderParameter renderParameter,
            RenderStatePatch renderState,
            CompiledRenderState compiledRenderState,
            ResourceBindingPlan bindingPlan,
            boolean shouldSwitchRenderState,
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey) {
        this(
                renderParameter,
                renderState,
                compiledRenderState,
                bindingPlan,
                shouldSwitchRenderState,
                shaderId,
                shaderVariantKey,
                deriveVertexLayoutKey(renderParameter),
                deriveRenderTargetKey(renderState),
                deriveResourceLayoutKey(bindingPlan));
    }

    public PipelineStateKey(
            RenderParameter renderParameter,
            RenderStatePatch renderState,
            CompiledRenderState compiledRenderState,
            ResourceBindingPlan bindingPlan,
            boolean shouldSwitchRenderState,
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey,
            KeyId vertexLayoutKey,
            KeyId renderTargetKey,
            KeyId resourceLayoutKey) {
        this.renderParameter = renderParameter;
        this.renderState = renderState != null ? renderState : RenderStatePatch.empty();
        this.compiledRenderState = compiledRenderState != null
                ? compiledRenderState
                : RenderStateCompiler.compile(this.renderState);
        this.bindingPlan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.shaderId = shaderId != null ? shaderId : UNBOUND_SHADER;
        this.shaderVariantKey = shaderVariantKey != null ? shaderVariantKey : ShaderVariantKey.EMPTY;
        this.vertexLayoutKey = vertexLayoutKey != null ? vertexLayoutKey : EMPTY_VERTEX_LAYOUT;
        this.renderTargetKey = renderTargetKey != null ? renderTargetKey : DEFAULT_RENDER_TARGET;
        this.resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : ResourceBindingPlan.empty().layoutKey();
        this.rasterStateSignature = this.compiledRenderState != null && this.compiledRenderState.pipelineRasterState() != null
                ? this.compiledRenderState.pipelineRasterState().hashCode()
                : 0;
        this.resourceBindingSignature = shouldIncludeResourceBindingSignature(renderParameter)
                ? this.bindingPlan.resourceBindingHash()
                : 0;
        this.hashCode = Objects.hash(
                this.renderParameter,
                this.rasterStateSignature,
                this.shouldSwitchRenderState,
                this.shaderId,
                this.shaderVariantKey,
                this.vertexLayoutKey,
                this.renderTargetKey,
                this.resourceLayoutKey,
                this.resourceBindingSignature);
    }

    public static PipelineStateKey from(RenderSetting renderSetting) {
        Objects.requireNonNull(renderSetting, "renderSetting");
        ShaderState shaderState = null;
        if (renderSetting.renderState() != null && renderSetting.renderState().get(ShaderState.TYPE) instanceof ShaderState state) {
            shaderState = state;
        }
        CompiledRenderState compiledRenderState = RenderStateCompiler.compile(renderSetting.renderState());
        return new PipelineStateKey(
                renderSetting.renderParameter(),
                renderSetting.renderState(),
                compiledRenderState,
                ResourceBindingPlan.from(renderSetting.resourceBinding()),
                renderSetting.shouldSwitchRenderState(),
                shaderState != null ? shaderState.getShaderId() : UNBOUND_SHADER,
                shaderState != null ? shaderState.getVariantKey() : ShaderVariantKey.EMPTY,
                deriveVertexLayoutKey(renderSetting.renderParameter()),
                deriveRenderTargetKey(renderSetting.targetBinding(), renderSetting.renderState()),
                deriveResourceLayoutKey(ResourceBindingPlan.from(renderSetting.resourceBinding())));
    }

    public static PipelineStateKey syntheticRaster(
            KeyId shaderId,
            KeyId vertexLayoutKey,
            KeyId renderTargetKey,
            KeyId resourceLayoutKey) {
        return new PipelineStateKey(
                null,
                RenderStatePatch.empty(),
                CompiledRenderState.empty(),
                ResourceBindingPlan.empty(),
                false,
                shaderId,
                ShaderVariantKey.EMPTY,
                vertexLayoutKey,
                renderTargetKey,
                resourceLayoutKey);
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public RenderStatePatch renderState() {
        return renderState;
    }

    public CompiledRenderState compiledRenderState() {
        return compiledRenderState;
    }

    public ResourceBindingPlan bindingPlan() {
        return bindingPlan;
    }

    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    public KeyId shaderId() {
        return shaderId;
    }

    public ShaderVariantKey shaderVariantKey() {
        return shaderVariantKey;
    }

    public KeyId vertexLayoutKey() {
        return vertexLayoutKey;
    }

    public KeyId renderTargetKey() {
        return renderTargetKey;
    }

    public KeyId resourceLayoutKey() {
        return resourceLayoutKey;
    }

    public int rasterStateSignature() {
        return rasterStateSignature;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PipelineStateKey other)) {
            return false;
        }
        return shouldSwitchRenderState == other.shouldSwitchRenderState
                && Objects.equals(renderParameter, other.renderParameter)
                && Objects.equals(shaderId, other.shaderId)
                && Objects.equals(shaderVariantKey, other.shaderVariantKey)
                && Objects.equals(vertexLayoutKey, other.vertexLayoutKey)
                && Objects.equals(renderTargetKey, other.renderTargetKey)
                && Objects.equals(resourceLayoutKey, other.resourceLayoutKey)
                && rasterStateSignature == other.rasterStateSignature
                && resourceBindingSignature == other.resourceBindingSignature;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static boolean shouldIncludeResourceBindingSignature(RenderParameter renderParameter) {
        return renderParameter == null || !renderParameter.isRasterization();
    }

    private static KeyId deriveVertexLayoutKey(RenderParameter renderParameter) {
        if (renderParameter == null || renderParameter.getLayout() == null) {
            return EMPTY_VERTEX_LAYOUT;
        }
        return KeyId.of("sketch:vertex_layout_" + Integer.toHexString(renderParameter.getLayout().hashCode()));
    }

    private static KeyId deriveRenderTargetKey(TargetBinding targetBinding, RenderStatePatch renderState) {
        if (targetBinding != null) {
            return targetBinding.renderTargetId();
        }
        if (renderState == null || renderState.isEmpty()) {
            return DEFAULT_RENDER_TARGET;
        }
        Object state = renderState.get(RenderTargetState.TYPE);
        if (state instanceof RenderTargetState renderTargetState) {
            return renderTargetState.renderTargetId();
        }
        return DEFAULT_RENDER_TARGET;
    }

    private static KeyId deriveRenderTargetKey(RenderStatePatch renderState) {
        return deriveRenderTargetKey(null, renderState);
    }

    private static KeyId deriveResourceLayoutKey(ResourceBindingPlan bindingPlan) {
        return bindingPlan != null ? bindingPlan.layoutKey() : ResourceBindingPlan.empty().layoutKey();
    }
}

