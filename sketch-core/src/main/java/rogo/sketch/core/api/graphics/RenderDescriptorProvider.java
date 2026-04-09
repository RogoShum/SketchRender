package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;

/**
 * Formal graphics-side provider for V2 render descriptors/materials.
 * <p>
 * Implementations expose whether the descriptor is stable or dynamic, plus a
 * version used when dynamic descriptor recompilation is required.
 * </p>
 * <p>
 * {@link DescriptorStability#STABLE} means the descriptor should remain
 * cache-friendly after initial compilation. {@link DescriptorStability#DYNAMIC}
 * means the graphics may switch descriptor/material state at runtime and the
 * pipeline should only rebuild descriptor-facing group keys when
 * {@link #descriptorVersion()} changes.
 * </p>
 */
public interface RenderDescriptorProvider {
    default DescriptorStability descriptorStability() {
        return DescriptorStability.STABLE;
    }

    long descriptorVersion();

    CompiledRenderSetting buildRenderDescriptor(RenderParameter renderParameter);
}

