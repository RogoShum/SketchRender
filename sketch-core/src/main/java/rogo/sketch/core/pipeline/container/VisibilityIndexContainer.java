package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;

import java.util.List;

/**
 * Ordered visibility index contract.
 * <p>
 * Implementations should return visible instances in the exact order expected
 * by the renderer (insertion order, priority order, tree traversal order, etc.).
 * </p>
 */
public interface VisibilityIndexContainer<C extends RenderContext> extends GraphicsContainer<C> {
    @Override
    List<Graphics> getVisibleInstances(C context);
}