package rogo.sketch.core.pipeline.async;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Async render executor for parallelizing compute-intensive operations
 * such as tick processing and uniform collection
 */
public class AsyncRenderExecutor {
    private static final AsyncRenderExecutor INSTANCE = new AsyncRenderExecutor();
    
    // Configuration parameters
    private static final int TICK_THREAD_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int UNIFORM_THREAD_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);
    private static final int BATCH_SIZE = 64; // Number of instances per work unit
    
    // Thread pools
    private final ExecutorService tickExecutor;
    private final ExecutorService uniformExecutor;
    private final ForkJoinPool forkJoinPool;
    
    // Performance monitoring
    private volatile long lastTickTime = 0;
    private volatile long lastUniformCollectionTime = 0;
    
    private AsyncRenderExecutor() {
        // Dedicated tick thread pool - high priority
        this.tickExecutor = new ThreadPoolExecutor(
                TICK_THREAD_POOL_SIZE, TICK_THREAD_POOL_SIZE,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "RenderTick-" + r.hashCode());
                    t.setPriority(Thread.NORM_PRIORITY + 1);
                    t.setDaemon(true);
                    return t;
                }
        );
        
        // Dedicated uniform collection thread pool - standard priority
        this.uniformExecutor = new ThreadPoolExecutor(
                UNIFORM_THREAD_POOL_SIZE, UNIFORM_THREAD_POOL_SIZE,
                1L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "UniformCollector-" + r.hashCode());
                    t.setPriority(Thread.NORM_PRIORITY);
                    t.setDaemon(true);
                    return t;
                }
        );
        
        // Common Fork-Join pool for divide-and-conquer tasks
        this.forkJoinPool = ForkJoinPool.commonPool();
    }
    
    public static AsyncRenderExecutor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Execute tick operations asynchronously in parallel
     */
    public <C extends RenderContext> CompletableFuture<Void> tickInstancesAsync(
            Collection<Graphics> instances, C context) {
        
        if (instances.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        long startTime = System.nanoTime();
        
        // Partition instances into batches
        List<List<Graphics>> batches = partitionInstances(instances, BATCH_SIZE);
        
        // Create async tasks
        List<CompletableFuture<Void>> tickTasks = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    for (Graphics instance : batch) {
                        if (instance.shouldTick()) {
                            try {
                                instance.tick(context);
                            } catch (Exception e) {
                                System.err.println("Error ticking instance " + instance.getIdentifier() + ": " + e.getMessage());
                            }
                        }
                    }
                }, tickExecutor))
                .toList();
        
        // Wait for all tick operations to complete
        return CompletableFuture.allOf(tickTasks.toArray(new CompletableFuture[0]))
                .whenComplete((result, throwable) -> {
                    lastTickTime = System.nanoTime() - startTime;
                    if (throwable != null) {
                        System.err.println("Async tick failed: " + throwable.getMessage());
                    }
                });
    }
    
    /**
     * Collect uniform snapshots asynchronously in parallel
     */
    public <C extends RenderContext> CompletableFuture<List<UniformCollectionResult>> collectUniformsAsync(
            Collection<Graphics> instances,
            Function<Graphics, UniformValueSnapshot> snapshotCollector) {
        
        if (instances.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        
        long startTime = System.nanoTime();
        
        // Partition instances into batches
        List<List<Graphics>> batches = partitionInstances(instances, BATCH_SIZE);
        
        // Create async collection tasks
        List<CompletableFuture<List<UniformCollectionResult>>> collectionTasks = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(() -> {
                    return batch.stream()
                            .filter(Graphics::shouldRender)
                            .map(instance -> {
                                try {
                                    UniformValueSnapshot snapshot = snapshotCollector.apply(instance);
                                    return new UniformCollectionResult(instance, snapshot, null);
                                } catch (Exception e) {
                                    return new UniformCollectionResult(instance, null, e);
                                }
                            })
                            .toList();
                }, uniformExecutor))
                .toList();
        
        // Merge all results
        return CompletableFuture.allOf(collectionTasks.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    lastUniformCollectionTime = System.nanoTime() - startTime;
                    return collectionTasks.stream()
                            .flatMap(task -> task.join().stream())
                            .toList();
                });
    }
    
    /**
     * Process large-scale parallel operations using Fork-Join
     */
    public <T, R> CompletableFuture<List<R>> processInParallel(
            Collection<T> items, 
            Function<T, R> processor, 
            int threshold) {
        
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            return items.parallelStream()
                    .map(processor)
                    .toList();
        }, forkJoinPool);
    }
    
    /**
     * Partition instance collection into batches
     */
    private List<List<Graphics>> partitionInstances(Collection<Graphics> instances, int batchSize) {
        List<Graphics> instanceList = instances instanceof List ?
                (List<Graphics>) instances :
                List.copyOf(instances);
        
        List<List<Graphics>> batches = new java.util.ArrayList<>();
        for (int i = 0; i < instanceList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, instanceList.size());
            batches.add(instanceList.subList(i, end));
        }
        return batches;
    }
    
    /**
     * Get performance statistics
     */
    public AsyncPerformanceStats getPerformanceStats() {
        return new AsyncPerformanceStats(
                lastTickTime / 1_000_000.0, // Convert to milliseconds
                lastUniformCollectionTime / 1_000_000.0,
                ((ThreadPoolExecutor) tickExecutor).getActiveCount(),
                ((ThreadPoolExecutor) uniformExecutor).getActiveCount(),
                forkJoinPool.getActiveThreadCount()
        );
    }
    
    /**
     * Check if async processing is suitable for the given instance count
     */
    public boolean shouldUseAsync(int instanceCount) {
        // For small instance counts, async overhead may outweigh benefits
        return instanceCount >= BATCH_SIZE;
    }
    
    /**
     * Shutdown the executor
     */
    public void shutdown() {
        tickExecutor.shutdown();
        uniformExecutor.shutdown();
        // forkJoinPool is a common pool, no need to shut down
        
        try {
            if (!tickExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                tickExecutor.shutdownNow();
            }
            if (!uniformExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                uniformExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            tickExecutor.shutdownNow();
            uniformExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Uniform collection result
     */
    public record UniformCollectionResult(
            Graphics instance,
            UniformValueSnapshot snapshot,
            Exception error
    ) {
        public boolean isSuccess() {
            return error == null && snapshot != null;
        }
    }
    
    /**
     * Async performance statistics
     */
    public record AsyncPerformanceStats(
            double lastTickTimeMs,
            double lastUniformCollectionTimeMs,
            int activeTickThreads,
            int activeUniformThreads,
            int activeForkJoinThreads
    ) {
        @Override
        public String toString() {
            return String.format(
                    "AsyncStats{tick=%.2fms, uniform=%.2fms, threads=[tick=%d, uniform=%d, fj=%d]}",
                    lastTickTimeMs, lastUniformCollectionTimeMs,
                    activeTickThreads, activeUniformThreads, activeForkJoinThreads
            );
        }
    }
}