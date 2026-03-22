package rogo.sketch.core.api.graphics;

import org.jetbrains.annotations.Nullable;

/**
 * Optional parent relationship provider for transform-authored graphics.
 */
public interface TransformParentSource {
    @Nullable
    Graphics getTransformParent();
}
