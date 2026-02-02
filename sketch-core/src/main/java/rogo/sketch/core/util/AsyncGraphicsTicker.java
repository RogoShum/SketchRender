package rogo.sketch.core.util;

import rogo.sketch.core.pipeline.GraphicsPipeline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncGraphicsTicker {
    private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();
    private final GraphicsPipeline<?> pipeline;
    private CompletableFuture<Void> currentTask = CompletableFuture.completedFuture(null);

    public AsyncGraphicsTicker(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }

    public void onPostTick() {
        currentTask = CompletableFuture.runAsync(() -> {
            pipeline.asyncTickGraphics();
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