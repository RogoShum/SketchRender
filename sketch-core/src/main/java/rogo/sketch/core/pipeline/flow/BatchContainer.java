package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.flow.container.ContainerDescriptor;
import rogo.sketch.core.pipeline.flow.impl.ContainerListener;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

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
public interface BatchContainer<G extends Graphics, T extends InstanceInfo<G>> {

    /**
     * Register a graphics instance to the appropriate batch.
     * <p>
     * Creates or finds the appropriate batch and adds the instance.
     * </p>
     *
     * @param graphics        The graphics instance to register
     * @param renderParameter The render parameter for batch assignment
     */
    void registerInstance(G graphics, RenderParameter renderParameter);

    /**
     * Register a graphics instance into a specific graphics container.
     * <p>
     * This is the descriptor-aware API for merged container mode.
     * The default implementation falls back to {@link #registerInstance}.
     * </p>
     */
    default void addInstance(
            G graphics,
            RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        registerInstance(graphics, renderParameter);
    }

    /**
     * Type-safe facade for untyped callers.
     */
    default void addGraphicsInstance(Graphics graphics, RenderParameter renderParameter) {
        addGraphicsInstance(graphics, renderParameter, null, null);
    }

    /**
     * Type-safe facade for untyped callers with descriptor-aware container routing.
     */
    default void addGraphicsInstance(
            Graphics graphics,
            RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        if (graphics == null) {
            return;
        }
        if (!getGraphicsType().isInstance(graphics)) {
            throw new IllegalArgumentException(
                    "Unsupported graphics type " + graphics.getClass().getName() + " for container " + getClass().getSimpleName());
        }
        G typed = getGraphicsType().cast(graphics);
        if (containerId == null && supplier == null) {
            registerInstance(typed, renderParameter);
        } else {
            addInstance(typed, renderParameter, containerId, supplier);
        }
    }

    /**
     * Unregister a graphics instance from its batch.
     * <p>
     * Removes the instance from its assigned batch.
     * </p>
     *
     * @param graphics The graphics instance to unregister
     */
    void unregisterInstance(Graphics graphics);

    /**
     * Remove an instance from this container.
     * Default implementation delegates to {@link #unregisterInstance}.
     */
    default void removeInstance(Graphics graphics) {
        unregisterInstance(graphics);
    }

    /**
     * Type-safe facade for untyped callers.
     */
    default void removeGraphicsInstance(Graphics graphics) {
        unregisterInstance(graphics);
    }

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
     * Prepare visibility snapshots for current frame.
     * <p>
     * Implementations may compute visible subsets per batch so strategies can
     * consume prefiltered instances directly.
     * </p>
     */
    default void prepareVisibility(RenderContext context) {
        // Default: no-op. Implementations may keep strategy-side filtering.
    }

    /**
     * Prepare for a new frame.
     * <p>
     * Called at the start of each frame to reset visibility state
     * and prepare for visibility filtering.
     * </p>
     */
    void prepareForFrame();

    /**
     * Tick all tracked instances.
     */
    default void tick(RenderContext context) {
        // Default: no-op for legacy implementations.
    }

    /**
     * Async tick all tracked instances.
     */
    default void asyncTick(RenderContext context) {
        // Default: no-op for legacy implementations.
    }

    /**
     * Swap async data for all tracked instances.
     */
    default void swapData() {
        // Default: no-op for legacy implementations.
    }

    /**
     * Clear all batches and instance assignments.
     */
    void clear();

    /**
     * Register a descriptor for lazy graphics container creation.
     */
    default void registerContainerDescriptor(ContainerDescriptor<? extends RenderContext> descriptor) {
        // Default: no-op for legacy implementations.
    }

    /**
     * Register an external container listener. Implementations should attach this
     * listener to current and future graphics containers.
     */
    default void registerContainerListener(ContainerListener listener) {
        // Default: no-op for legacy implementations.
    }

    /**
     * Unregister an external container listener.
     */
    default void unregisterContainerListener(ContainerListener listener) {
        // Default: no-op for legacy implementations.
    }

    /**
     * Retrieve an active graphics container by id.
     */
    default GraphicsContainer<? extends RenderContext> getGraphicsContainer(KeyId id) {
        return null;
    }

    /**
     * Create or retrieve a graphics container by id.
     */
    default GraphicsContainer<? extends RenderContext> getOrCreateGraphicsContainer(
            KeyId id,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        return getGraphicsContainer(id);
    }

    /**
     * Get a read-only view of active graphics containers.
     */
    default Map<KeyId, GraphicsContainer<? extends RenderContext>> getActiveGraphicsContainers() {
        return Collections.emptyMap();
    }

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