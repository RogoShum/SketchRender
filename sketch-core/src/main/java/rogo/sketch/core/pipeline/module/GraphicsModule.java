package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Extension of {@link PipelineModule} that can claim ownership of graphics instances.
 * <p>
 * When a graphics instance is added to the pipeline, the {@link ModuleRegistry}
 * checks each registered {@code GraphicsModule} via {@link #supports(Graphics)}.
 * If it returns true, the instance is attached to this module via
 * {@link #onAttach(Graphics, RenderParameter, KeyId)}.
 * When the instance is removed, {@link #onDetach(Graphics)} is called.
 * </p>
 * <p>
 * This replaces the old {@code ContainerListener} pattern with an explicit,
 * module-driven design.
 * </p>
 */
public interface GraphicsModule extends PipelineModule {

    /**
     * Check if this module is interested in the given graphics instance.
     *
     * @param graphics The graphics instance being added
     * @return true if this module should track this instance
     */
    boolean supports(Graphics graphics);

    /**
     * Called when a supported graphics instance is added to the pipeline.
     *
     * @param graphics        The graphics instance
     * @param renderParameter The render parameter
     * @param containerType   The container type id
     */
    void onAttach(Graphics graphics, RenderParameter renderParameter, KeyId containerType);

    /**
     * Called when a previously attached graphics instance is removed.
     *
     * @param graphics The graphics instance being removed
     */
    void onDetach(Graphics graphics);
}