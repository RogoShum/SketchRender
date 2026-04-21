package rogo.sketch.core.driver.state;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.component.BlendState;
import rogo.sketch.core.driver.state.component.ColorMaskState;
import rogo.sketch.core.driver.state.component.CullState;
import rogo.sketch.core.driver.state.component.DepthMaskState;
import rogo.sketch.core.driver.state.component.DepthTestState;
import rogo.sketch.core.driver.state.component.LogicOpState;
import rogo.sketch.core.driver.state.component.PolygonOffsetState;
import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.component.ScissorState;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.driver.state.component.StencilState;
import rogo.sketch.core.driver.state.component.ViewportState;
import rogo.sketch.core.util.KeyId;

public final class RasterStateCompiler {
    private RasterStateCompiler() {
    }

    public static CompiledRasterState compile(RenderStatePatch patch) {
        RenderStatePatch resolvedPatch = patch != null ? patch : RenderStatePatch.empty();

        BlendState blendState = resolve(resolvedPatch, BlendState.TYPE, BlendState.class);
        DepthTestState depthTestState = resolve(resolvedPatch, DepthTestState.TYPE, DepthTestState.class);
        DepthMaskState depthMaskState = resolve(resolvedPatch, DepthMaskState.TYPE, DepthMaskState.class);
        StencilState stencilState = resolve(resolvedPatch, StencilState.TYPE, StencilState.class);
        CullState cullState = resolve(resolvedPatch, CullState.TYPE, CullState.class);
        PolygonOffsetState polygonOffsetState = resolve(resolvedPatch, PolygonOffsetState.TYPE, PolygonOffsetState.class);
        ColorMaskState colorMaskState = resolve(resolvedPatch, ColorMaskState.TYPE, ColorMaskState.class);
        ViewportState viewportState = resolve(resolvedPatch, ViewportState.TYPE, ViewportState.class);
        ScissorState scissorState = resolve(resolvedPatch, ScissorState.TYPE, ScissorState.class);
        LogicOpState logicOpState = resolve(resolvedPatch, LogicOpState.TYPE, LogicOpState.class);
        RenderTargetState renderTargetState = resolve(resolvedPatch, RenderTargetState.TYPE, RenderTargetState.class);
        ShaderState shaderState = resolve(resolvedPatch, ShaderState.TYPE, ShaderState.class);

        DepthState depthState = new DepthState(
                depthTestState != null && depthTestState.enabled(),
                depthTestState != null ? depthTestState.compareOp() : CompareOp.LESS,
                depthMaskState == null || depthMaskState.writable());

        return new CompiledRasterState(
                resolvedPatch,
                new PipelineRasterState(
                        blendState,
                        depthState,
                        stencilState,
                        cullState,
                        polygonOffsetState,
                        colorMaskState),
                new DynamicRenderState(
                        viewportState,
                        scissorState,
                        logicOpState),
                new AttachmentBindingState(renderTargetState),
                new ShaderBindingState(shaderState));
    }

    private static <T extends RenderStateComponent> T resolve(RenderStatePatch patch, KeyId identifier, Class<T> type) {
        RenderStateComponent override = patch != null ? patch.get(identifier) : null;
        if (type.isInstance(override)) {
            return type.cast(override);
        }
        RenderStateComponent defaultComponent = DefaultRenderStates.getDefaultComponent(identifier);
        return type.isInstance(defaultComponent) ? type.cast(defaultComponent) : null;
    }
}
