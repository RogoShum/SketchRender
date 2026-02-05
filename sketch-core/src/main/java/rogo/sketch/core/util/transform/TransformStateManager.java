package rogo.sketch.core.util.transform;

import rogo.sketch.core.pipeline.GraphicsPipeline;

/**
 * TransformStateManager - Global state for the Transform system.
 * <p>
 * Holds the static MatrixManager instance and provides access to transform-related
 * resources for compute shader processing.
 * <p>
 * Tick Flow:
 * <pre>
 * PRE TICK (Phase.START):
 *   1. AsyncGraphicsTicker.onPreTick() - waits for async task, swaps data
 *   2. TransformStateManager.onPreTick() - upload async buffer, dispatch CS
 *   3. tickLogic()
 *
 * POST TICK (Phase.END):
 *   1. tickGraphics() - sync tick for sync graphics
 *   2. TransformStateManager.onPostTick() - upload sync buffer, dispatch CS
 *   3. AsyncGraphicsTicker.onPostTick() - starts async task
 *   4. (Async) asyncTickGraphics() + onAsyncTickComplete() - write to async buffer
 * </pre>
 */
public class TransformStateManager {
    private final GraphicsPipeline<?> graphicsPipeline;
    public MatrixManager matrixManager = null;


    public TransformStateManager(GraphicsPipeline<?> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
        initialize();
    }

    /**
     * Initialize the transform system.
     * Should be called during pipeline initialization.
     */
    public void initialize() {
        if (matrixManager == null) {
            matrixManager = new MatrixManager();

            // Register MatrixManager as a global container listener
            // This enables automatic transform registration when TransformableGraphics are added/removed
            graphicsPipeline.registerGlobalContainerListener(matrixManager);
        }
    }

    /**
     * Cleanup the transform system.
     * Should be called during pipeline shutdown.
     */
    public void cleanup() {
        if (matrixManager != null) {
            // Unregister from pipeline before cleanup
            try {
                graphicsPipeline.unregisterGlobalContainerListener(matrixManager);
            } catch (Exception ignored) {
                // Pipeline may already be cleaned up
            }
            matrixManager.cleanup();
            matrixManager = null;
        }
    }

    /**
     * Check if the transform system is initialized.
     */
    public boolean isInitialized() {
        return matrixManager != null;
    }

    // ==================== Tick Hooks ====================

    /**
     * Called during PRE TICK (Phase.START) after data swap.
     * Uploads async buffer data to GPU and dispatches compute shader.
     * <p>
     * This processes data written by async graphics in the previous frame's worker thread.
     */
    public void onPreTick() {
        if (!isInitialized()) return;

        matrixManager.preRender();
    }

    /**
     * Called during POST TICK (Phase.END) after tickGraphics().
     * Writes sync transform data to buffer, uploads to GPU, and dispatches compute shader.
     * <p>
     * This processes data from sync graphics that were ticked in tickGraphics().
     */
    public void onPostTick() {
        if (!isInitialized()) return;

        // Write and upload sync transform data
        matrixManager.syncTick();
    }

    /**
     * Called in worker thread after asyncTickGraphics() completes.
     * Writes async transform data to buffer (NO GL calls - no context available).
     * <p>
     * This is set as the callback on AsyncGraphicsTicker during initialization.
     */
    public void onAsyncTickComplete() {
        if (!isInitialized()) return;

        // Write transform data to async buffer (no GL)
        matrixManager.asyncTick();
        matrixManager.swapTransformData();
    }
}