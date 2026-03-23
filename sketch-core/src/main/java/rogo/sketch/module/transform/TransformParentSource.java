package rogo.sketch.module.transform;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.Graphics;

/**
 * Optional parent relationship provider for transform-authored graphics.
 */
public interface TransformParentSource {
    @Nullable
    Graphics getTransformParent();
}
