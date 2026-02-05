package rogo.sketch.core.util;

import rogo.sketch.core.pipeline.GraphicsPipeline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncGraphicsTicker {
    private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();
    private final GraphicsPipeline<?> pipeline;
    private CompletableFuture<Void> currentTask = CompletableFuture.completedFuture(null);
    
    // Callback for post-async-tick processing (no GL context available)
    private Runnable asyncTickCompleteCallback;

    public AsyncGraphicsTicker(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }
    
    /**
     * Set a callback that will be called after asyncTickGraphics() completes.
     * This callback runs in the async thread, so no GL calls are allowed.
     * 
     * @param callback The callback to run after async tick
     */
    public void setAsyncTickCompleteCallback(Runnable callback) {
        this.asyncTickCompleteCallback = callback;
    }

    public void onPostTick() {
        currentTask = CompletableFuture.runAsync(() -> {
            pipeline.asyncTickGraphics();
            // Call the callback after async tick completes (still in async thread)
            if (asyncTickCompleteCallback != null) {
                asyncTickCompleteCallback.run();
            }
        }, EXECUTOR);
    }

    public void onPreTick() {
        if (!currentTask.isDone()) {
            try {
                currentTask.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        pipeline.swapGraphicsData();
    }
}