package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;

/**
 * Optional graphics-side provider for V2 render descriptors.
 * <p>
 * Implementations can expose stable descriptor versions so the pipeline can
 * invalidate only state/resource compilation without rebuilding unrelated
 * geometry or visibility data.
 * </p>
 */
public interface RenderDescriptorProvider {
    long descriptorVersion();

    CompiledRenderSetting buildRenderDescriptor(RenderParameter renderParameter);
}
