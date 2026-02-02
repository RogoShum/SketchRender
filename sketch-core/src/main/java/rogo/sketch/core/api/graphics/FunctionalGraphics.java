package rogo.sketch.core.api.graphics;

import rogo.sketch.core.instance.FunctionGraphics;
import rogo.sketch.core.pipeline.RenderContext;

/**
 * Marker interface for graphics that execute function-type operations.
 * <p>
 * Function graphics are simple execution units that don't require
 * mesh data or compute shader dispatch.
 * </p>
 * <p>
 * Implementations should:
 * <ul>
 *   <li>Extend the concrete {@link FunctionGraphics} class</li>
 *   <li>Implement {@link #execute(RenderContext)} for custom execution logic</li>
 * </ul>
 * </p>
 * 
 * @see FunctionGraphics
 */
public interface FunctionalGraphics extends Graphics {
    
    /**
     * Execute the function logic.
     *
     * @param context The render context
     */
    void execute(RenderContext context);
}