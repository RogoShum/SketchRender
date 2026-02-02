package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.container.ContainerListener;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;

import java.util.Collection;

/**
 * Container for managing RenderBatches with automatic instance assignment.
 * <p>
 * BatchContainer implements {@link ContainerListener} to receive notifications
 * from {@link rogo.sketch.core.pipeline.container.GraphicsContainer} and automatically
 * assign graphics instances to appropriate batches based on their RenderSetting
 * and other grouping criteria.
 * </p>
 * <p>
 * Unlike per-frame batch reconstruction, BatchContainer maintains persistent batches
 * and only updates when instances are added, removed, or become dirty.
 * </p>
 *
 * @param <G> The type of graphics this container handles
 * @param <T> The type of instance info this container produces
 */
public interface BatchContainer<G extends Graphics, T extends InstanceInfo<G>> extends ContainerListener {

    /**
     * Register a graphics instance to the appropriate batch.
     * <p>
     * This is typically called from {@link #onInstanceAdded}.
     * Creates or finds the appropriate batch and adds the instance.
     * </p>
     *
     * @param graphics        The graphics instance to register
     * @param renderParameter The render parameter for batch assignment
     */
    void registerInstance(G graphics, RenderParameter renderParameter);

    /**
     * Unregister a graphics instance from its batch.
     * <p>
     * This is typically called from {@link #onInstanceRemoved}.
     * Removes the instance from its assigned batch.
     * </p>
     *
     * @param graphics The graphics instance to unregister
     */
    void unregisterInstance(Graphics graphics);

    /**
     * Handle a dirty instance by reassigning it to the correct batch.
     * <p>
     * Called when an instance's render setting or mesh changes,
     * requiring it to be moved to a different batch.
     * </p>
     *
     * @param graphics The dirty graphics instance
     */
    void handleDirtyInstance(G graphics);

    /**
     * Get all batches managed by this container, including empty ones.
     *
     * @return Collection of all batches
     */
    Collection<RenderBatch<T>> getAllBatches();

    /**
     * Get only non-empty batches.
     *
     * @return Collection of active batches with instances
     */
    Collection<RenderBatch<T>> getActiveBatches();

    /**
     * Prepare for a new frame.
     * <p>
     * Called at the start of each frame to reset visibility state
     * and prepare for visibility filtering.
     * </p>
     */
    void prepareForFrame();

    /**
     * Clear all batches and instance assignments.
     */
    void clear();

    /**
     * Get the expected graphics type for this container.
     *
     * @return The graphics class
     */
    Class<G> getGraphicsType();

    /**
     * Get the expected instance info type for this container.
     *
     * @return The instance info class
     */
    Class<T> getInfoType();
}