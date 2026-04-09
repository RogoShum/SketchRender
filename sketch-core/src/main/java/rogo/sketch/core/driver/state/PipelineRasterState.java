package rogo.sketch.core.driver.state;

import rogo.sketch.core.driver.state.component.BlendState;
import rogo.sketch.core.driver.state.component.ColorMaskState;
import rogo.sketch.core.driver.state.component.CullState;
import rogo.sketch.core.driver.state.component.PolygonOffsetState;
import rogo.sketch.core.driver.state.component.StencilState;

/**
 * Fixed rasterization state consumed by backends.
 */
public record PipelineRasterState(
        BlendState blendState,
        DepthState depthState,
        StencilState stencilState,
        CullState cullState,
        PolygonOffsetState polygonOffsetState,
        ColorMaskState colorMaskState
) {
}

