package rogo.sketch.core.packet;

import rogo.sketch.core.driver.state.CompiledRasterState;
import rogo.sketch.core.driver.state.RasterStateCompiler;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.pipeline.PipelineConfig;
import rogo.sketch.core.pipeline.RasterRenderSetting;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public class RasterPipelineKey implements ExecutionKey {
    private static final KeyId UNBOUND_SHADER = KeyId.of("sketch:unbound_shader");
    private static final KeyId EMPTY_VERTEX_LAYOUT = KeyId.of("sketch:empty_vertex_layout");

    private final ExecutionDomain domain;
    private final RenderParameter renderParameter;
    private final RenderStatePatch renderState;
    private final CompiledRasterState compiledRasterState;
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

    public RasterPipelineKey(
            RenderParameter renderParameter,
            RenderStatePatch renderState,
            CompiledRasterState compiledRasterState,
            ResourceBindingPlan bindingPlan,
            boolean shouldSwitchRenderState,
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey) {
        this(
                ExecutionDomain.RASTER,
                renderParameter,
                renderState,
                compiledRasterState,
                bindingPlan,
                shouldSwitchRenderState,
                shaderId,
                shaderVariantKey,
                deriveVertexLayoutKey(renderParameter),
                deriveRenderTargetKey(renderState),
                deriveResourceLayoutKey(bindingPlan));
    }

    protected RasterPipelineKey(
            ExecutionDomain domain,
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
        this.domain = domain != null ? domain : ExecutionDomain.RASTER;
        this.renderParameter = renderParameter;
        this.renderState = renderState != null ? renderState : RenderStatePatch.empty();
        this.compiledRasterState = compiledRasterState != null
                ? compiledRasterState
                : RasterStateCompiler.compile(this.renderState);
        this.bindingPlan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.shaderId = shaderId != null ? shaderId : UNBOUND_SHADER;
        this.shaderVariantKey = shaderVariantKey != null ? shaderVariantKey : ShaderVariantKey.EMPTY;
        this.vertexLayoutKey = vertexLayoutKey != null ? vertexLayoutKey : EMPTY_VERTEX_LAYOUT;
        this.renderTargetKey = renderTargetKey != null ? renderTargetKey : PipelineConfig.DEFAULT_RENDER_TARGET_ID;
        this.resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : ResourceBindingPlan.empty().layoutKey();
        this.rasterStateSignature = this.compiledRasterState != null && this.compiledRasterState.pipelineRasterState() != null
                ? this.compiledRasterState.pipelineRasterState().hashCode()
                : 0;
        this.resourceBindingSignature = this.bindingPlan.resourceBindingHash();
        this.hashCode = Objects.hash(
                this.domain,
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

    public static RasterPipelineKey from(RasterRenderSetting renderSetting, RenderParameter renderParameter) {
        Objects.requireNonNull(renderSetting, "renderSetting");
        ShaderState shaderState = null;
        if (renderSetting.renderState() != null
                && renderSetting.renderState().get(ShaderState.TYPE) instanceof ShaderState state) {
            shaderState = state;
        }
        CompiledRasterState compiledRasterState = RasterStateCompiler.compile(renderSetting.renderState());
        ResourceBindingPlan bindingPlan = ResourceBindingPlan.from(renderSetting.resourceBinding());
        return new RasterPipelineKey(
                ExecutionDomain.RASTER,
                renderParameter,
                renderSetting.renderState(),
                compiledRasterState,
                bindingPlan,
                renderSetting.shouldSwitchRenderState(),
                shaderState != null ? shaderState.getShaderId() : UNBOUND_SHADER,
                shaderState != null ? shaderState.getVariantKey() : ShaderVariantKey.EMPTY,
                deriveVertexLayoutKey(renderParameter),
                deriveRenderTargetKey(renderSetting.targetBinding(), renderSetting.renderState()),
                deriveResourceLayoutKey(bindingPlan));
    }

    public static RasterPipelineKey syntheticRaster(
            KeyId shaderId,
            KeyId vertexLayoutKey,
            KeyId renderTargetKey,
            KeyId resourceLayoutKey) {
        return new RasterPipelineKey(
                ExecutionDomain.RASTER,
                null,
                RenderStatePatch.empty(),
                CompiledRasterState.empty(),
                ResourceBindingPlan.empty(),
                false,
                shaderId,
                ShaderVariantKey.EMPTY,
                vertexLayoutKey,
                renderTargetKey,
                resourceLayoutKey);
    }

    @Override
    public ExecutionDomain domain() {
        return domain;
    }

    @Override
    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public RenderStatePatch renderState() {
        return renderState;
    }

    public CompiledRasterState compiledRasterState() {
        return compiledRasterState;
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
    public KeyId vertexLayoutKey() {
        return vertexLayoutKey;
    }

    @Override
    public KeyId renderTargetKey() {
        return renderTargetKey;
    }

    @Override
    public KeyId resourceLayoutKey() {
        return resourceLayoutKey;
    }

    @Override
    public int rasterStateSignature() {
        return rasterStateSignature;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RasterPipelineKey other)) {
            return false;
        }
        return shouldSwitchRenderState == other.shouldSwitchRenderState
                && domain == other.domain
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

    public static KeyId deriveVertexLayoutKey(RenderParameter renderParameter) {
        if (renderParameter == null || renderParameter.getLayout() == null) {
            return EMPTY_VERTEX_LAYOUT;
        }
        return KeyId.of("sketch:vertex_layout_" + Integer.toHexString(renderParameter.getLayout().hashCode()));
    }

    public static KeyId deriveRenderTargetKey(TargetBinding targetBinding, RenderStatePatch renderState) {
        if (targetBinding != null) {
            return targetBinding.renderTargetId();
        }
        if (renderState == null || renderState.isEmpty()) {
            return PipelineConfig.DEFAULT_RENDER_TARGET_ID;
        }
        Object state = renderState.get(RenderTargetState.TYPE);
        if (state instanceof RenderTargetState renderTargetState) {
            return renderTargetState.renderTargetId();
        }
        return PipelineConfig.DEFAULT_RENDER_TARGET_ID;
    }

    public static KeyId deriveRenderTargetKey(RenderStatePatch renderState) {
        return deriveRenderTargetKey(null, renderState);
    }

    public static KeyId deriveResourceLayoutKey(ResourceBindingPlan bindingPlan) {
        return bindingPlan != null ? bindingPlan.layoutKey() : ResourceBindingPlan.empty().layoutKey();
    }
}
