package rogo.sketch.core.driver.state;

import rogo.sketch.core.driver.state.component.LogicOpState;
import rogo.sketch.core.driver.state.component.ScissorState;
import rogo.sketch.core.driver.state.component.ViewportState;

/**
 * Runtime dynamic state that can change without rebuilding a raster pipeline.
 */
public record DynamicRenderState(
        ViewportState viewportState,
        ScissorState scissorState,
        LogicOpState logicOpState
) {
}

