package rogo.sketch.render.pipeline.async;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pool.InstancePoolManager;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central manager for asynchronous rendering operations with global configuration
 */
public class AsyncRenderManager {
    private static final AsyncRenderManager INSTANCE = new AsyncRenderManager();
    
    private final AsyncRenderConfig config = new AsyncRenderConfig();
    private volatile ThreadPoolExecutor executor;
    private final ScheduledExecutorService monitoringScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AsyncRender-Monitor");
        t.setDaemon(true);
        return t;
    });
    
    // Performance metrics
    private final AtomicLong totalAsyncOperations = new AtomicLong(0);
    private final AtomicLong totalSyncOperations = new AtomicLong(0);
    private final AtomicLong totalExecutionTimeNs = new AtomicLong(0);
    private volatile ScheduledFuture<?> monitoringTask;
    
    private AsyncRenderManager() {
        initializeExecutor();
        setupMonitoring();
    }
    
    public static AsyncRenderManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get current configuration
     */
    public AsyncRenderConfig getConfig() {
        return config;
    }
    
    /**
     * Update configuration and reinitialize if needed
     */
    public void updateConfig(AsyncRenderConfig newConfig) {
        boolean needsExecutorReinit = 
            config.getMaxThreads() != newConfig.getMaxThreads() ||
            config.getCoreThreads() != newConfig.getCoreThreads() ||
            config.getThreadKeepAliveMs() != newConfig.getThreadKeepAliveMs();
        
        // Copy configuration
        config.setGlobalMode(newConfig.getGlobalMode());
        config.setAsyncTickEnabled(newConfig.isAsyncTickEnabled());
        config.setAsyncUniformCollectionEnabled(newConfig.isAsyncUniformCollectionEnabled());
        config.setAsyncVertexFillEnabled(newConfig.isAsyncVertexFillEnabled());
        config.setAsyncInstanceUpdateEnabled(newConfig.isAsyncInstanceUpdateEnabled());
        config.setAsyncThreshold(newConfig.getAsyncThreshold());
        config.setVertexFillThreshold(newConfig.getVertexFillThreshold());
        config.setUniformCollectionThreshold(newConfig.getUniformCollectionThreshold());
        config.setInstanceUpdateThreshold(newConfig.getInstanceUpdateThreshold());
        config.setMaxThreads(newConfig.getMaxThreads());
        config.setCoreThreads(newConfig.getCoreThreads());
        config.setThreadKeepAliveMs(newConfig.getThreadKeepAliveMs());
        config.setPerformanceMonitoringEnabled(newConfig.isPerformanceMonitoringEnabled());
        config.setPerformanceReportIntervalMs(newConfig.getPerformanceReportIntervalMs());
        
        if (needsExecutorReinit) {
            reinitializeExecutor();
        }
        
        setupMonitoring();
    }
    
    /**
     * Execute instance tick operations
     */
    public CompletableFuture<Void> tickInstancesAsync(Collection<Graphics> instances, RenderContext context) {
        if (!config.isAsyncTickEnabled() || 
            !config.shouldUseAsync(instances.size(), config.getAsyncThreshold())) {
            return executeSync(() -> {
                tickInstancesSync(instances, context);
                return null;
            });
        }
        
        return executeAsync(() -> {
            tickInstancesSync(instances, context);
            return null;
        });
    }
    
    /**
     * Execute vertex filling operations
     */
    public <T> CompletableFuture<T> fillVertexAsync(Callable<T> vertexFillOperation, int workloadSize) {
        if (!config.isAsyncVertexFillEnabled() || 
            !config.shouldUseAsync(workloadSize, config.getVertexFillThreshold())) {
            return executeSync(vertexFillOperation);
        }
        
        return executeAsync(vertexFillOperation);
    }
    
    /**
     * Execute uniform collection operations
     */
    public <T> CompletableFuture<T> collectUniformsAsync(Callable<T> uniformCollectionOperation, int workloadSize) {
        if (!config.isAsyncUniformCollectionEnabled() || 
            !config.shouldUseAsync(workloadSize, config.getUniformCollectionThreshold())) {
            return executeSync(uniformCollectionOperation);
        }
        
        return executeAsync(uniformCollectionOperation);
    }
    
    /**
     * Execute instance update operations
     */
    public CompletableFuture<Void> updateInstancesAsync(Collection<Graphics> instances, RenderContext context) {
        if (!config.isAsyncInstanceUpdateEnabled() || 
            !config.shouldUseAsync(instances.size(), config.getInstanceUpdateThreshold())) {
            return executeSync(() -> {
                updateInstancesSync(instances, context);
                return null;
            });
        }
        
        return executeAsync(() -> {
            updateInstancesSync(instances, context);
            return null;
        });
    }
    
    /**
     * Determine if async should be used for given workload
     */
    public boolean shouldUseAsync(int workloadSize) {
        return config.shouldUseAsync(workloadSize, config.getAsyncThreshold());
    }
    
    /**
     * Get performance statistics
     */
    public AsyncPerformanceStats getPerformanceStats() {
        return new AsyncPerformanceStats(
            totalAsyncOperations.get(),
            totalSyncOperations.get(),
            totalExecutionTimeNs.get(),
            executor != null ? executor.getActiveCount() : 0,
            executor != null ? executor.getCompletedTaskCount() : 0
        );
    }
    
    /**
     * Shutdown the manager and cleanup resources
     */
    public void shutdown() {
        if (monitoringTask != null) {
            monitoringTask.cancel(false);
        }
        monitoringScheduler.shutdown();
        
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Private helper methods
    
    private void initializeExecutor() {
        if (executor != null) {
            executor.shutdown();
        }
        
        executor = new ThreadPoolExecutor(
            config.getCoreThreads(),
            config.getMaxThreads(),
            config.getThreadKeepAliveMs(),
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "AsyncRender-Worker");
                t.setDaemon(true);
                return t;
            }
        );
    }
    
    private void reinitializeExecutor() {
        ThreadPoolExecutor oldExecutor = executor;
        initializeExecutor();
        
        if (oldExecutor != null) {
            oldExecutor.shutdown();
        }
    }
    
    private void setupMonitoring() {
        if (monitoringTask != null) {
            monitoringTask.cancel(false);
        }
        
        if (config.isPerformanceMonitoringEnabled()) {
            monitoringTask = monitoringScheduler.scheduleAtFixedRate(
                this::reportPerformanceStats,
                config.getPerformanceReportIntervalMs(),
                config.getPerformanceReportIntervalMs(),
                TimeUnit.MILLISECONDS
            );
        }
    }
    
    private <T> CompletableFuture<T> executeAsync(Callable<T> operation) {
        totalAsyncOperations.incrementAndGet();
        long startTime = System.nanoTime();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = operation.call();
                totalExecutionTimeNs.addAndGet(System.nanoTime() - startTime);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Async operation failed", e);
            }
        }, executor);
    }
    
    private <T> CompletableFuture<T> executeSync(Callable<T> operation) {
        totalSyncOperations.incrementAndGet();
        long startTime = System.nanoTime();
        
        try {
            T result = operation.call();
            totalExecutionTimeNs.addAndGet(System.nanoTime() - startTime);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private void tickInstancesSync(Collection<Graphics> instances, RenderContext context) {
        for (Graphics instance : instances) {
            if (instance.shouldTick()) {
                instance.tick(context);
            }
        }
    }
    
    private void updateInstancesSync(Collection<Graphics> instances, RenderContext context) {
        InstancePoolManager poolManager = InstancePoolManager.getInstance();
        
        for (Graphics instance : instances) {
            try {
                if (instance.shouldDiscard()) {
                    poolManager.returnInstance(instance);
                }
            } catch (Exception e) {
                System.err.println("Failed to update instance: " + e.getMessage());
            }
        }
    }
    
    private void reportPerformanceStats() {
        AsyncPerformanceStats stats = getPerformanceStats();
        System.out.println("AsyncRenderManager Performance: " + stats);
    }
    
    public record AsyncPerformanceStats(
        long totalAsyncOperations,
        long totalSyncOperations,
        long totalExecutionTimeNs,
        int activeThreads,
        long completedTasks
    ) {
        public double getAverageExecutionTimeMs() {
            long totalOps = totalAsyncOperations + totalSyncOperations;
            return totalOps > 0 ? (totalExecutionTimeNs / 1_000_000.0) / totalOps : 0.0;
        }
        
        public double getAsyncRatio() {
            long totalOps = totalAsyncOperations + totalSyncOperations;
            return totalOps > 0 ? (double) totalAsyncOperations / totalOps : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "AsyncStats[async=%d, sync=%d, avgTime=%.2fms, asyncRatio=%.2f%%, activeThreads=%d]",
                totalAsyncOperations, totalSyncOperations, getAverageExecutionTimeMs(),
                getAsyncRatio() * 100, activeThreads
            );
        }
    }
}
