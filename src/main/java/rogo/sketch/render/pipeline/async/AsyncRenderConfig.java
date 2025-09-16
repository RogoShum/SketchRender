package rogo.sketch.render.pipeline.async;

/**
 * Configuration for asynchronous rendering behavior
 */
public class AsyncRenderConfig {
    // Global execution mode
    private volatile RenderExecutionMode globalMode = RenderExecutionMode.ADAPTIVE;
    
    // Individual feature toggles
    private volatile boolean asyncTickEnabled = true;
    private volatile boolean asyncUniformCollectionEnabled = true;
    private volatile boolean asyncVertexFillEnabled = true;
    private volatile boolean asyncInstanceUpdateEnabled = true;
    
    // Thresholds for adaptive behavior
    private volatile int asyncThreshold = 32;
    private volatile int vertexFillThreshold = 16;
    private volatile int uniformCollectionThreshold = 24;
    private volatile int instanceUpdateThreshold = 20;
    
    // Thread pool configuration
    private volatile int maxThreads = Runtime.getRuntime().availableProcessors();
    private volatile int coreThreads = Math.max(2, maxThreads / 2);
    private volatile long threadKeepAliveMs = 60000; // 1 minute
    
    // Performance monitoring
    private volatile boolean performanceMonitoringEnabled = false;
    private volatile long performanceReportIntervalMs = 5000; // 5 seconds
    
    // Copy constructor for immutable snapshots
    public AsyncRenderConfig(AsyncRenderConfig other) {
        this.globalMode = other.globalMode;
        this.asyncTickEnabled = other.asyncTickEnabled;
        this.asyncUniformCollectionEnabled = other.asyncUniformCollectionEnabled;
        this.asyncVertexFillEnabled = other.asyncVertexFillEnabled;
        this.asyncInstanceUpdateEnabled = other.asyncInstanceUpdateEnabled;
        this.asyncThreshold = other.asyncThreshold;
        this.vertexFillThreshold = other.vertexFillThreshold;
        this.uniformCollectionThreshold = other.uniformCollectionThreshold;
        this.instanceUpdateThreshold = other.instanceUpdateThreshold;
        this.maxThreads = other.maxThreads;
        this.coreThreads = other.coreThreads;
        this.threadKeepAliveMs = other.threadKeepAliveMs;
        this.performanceMonitoringEnabled = other.performanceMonitoringEnabled;
        this.performanceReportIntervalMs = other.performanceReportIntervalMs;
    }
    
    public AsyncRenderConfig() {}
    
    // Global mode
    public RenderExecutionMode getGlobalMode() { return globalMode; }
    public void setGlobalMode(RenderExecutionMode mode) { this.globalMode = mode; }
    
    // Feature toggles
    public boolean isAsyncTickEnabled() { return asyncTickEnabled && isAsyncAllowed(); }
    public void setAsyncTickEnabled(boolean enabled) { this.asyncTickEnabled = enabled; }
    
    public boolean isAsyncUniformCollectionEnabled() { return asyncUniformCollectionEnabled && isAsyncAllowed(); }
    public void setAsyncUniformCollectionEnabled(boolean enabled) { this.asyncUniformCollectionEnabled = enabled; }
    
    public boolean isAsyncVertexFillEnabled() { return asyncVertexFillEnabled && isAsyncAllowed(); }
    public void setAsyncVertexFillEnabled(boolean enabled) { this.asyncVertexFillEnabled = enabled; }
    
    public boolean isAsyncInstanceUpdateEnabled() { return asyncInstanceUpdateEnabled && isAsyncAllowed(); }
    public void setAsyncInstanceUpdateEnabled(boolean enabled) { this.asyncInstanceUpdateEnabled = enabled; }
    
    // Thresholds
    public int getAsyncThreshold() { return asyncThreshold; }
    public void setAsyncThreshold(int threshold) { this.asyncThreshold = Math.max(1, threshold); }
    
    public int getVertexFillThreshold() { return vertexFillThreshold; }
    public void setVertexFillThreshold(int threshold) { this.vertexFillThreshold = Math.max(1, threshold); }
    
    public int getUniformCollectionThreshold() { return uniformCollectionThreshold; }
    public void setUniformCollectionThreshold(int threshold) { this.uniformCollectionThreshold = Math.max(1, threshold); }
    
    public int getInstanceUpdateThreshold() { return instanceUpdateThreshold; }
    public void setInstanceUpdateThreshold(int threshold) { this.instanceUpdateThreshold = Math.max(1, threshold); }
    
    // Thread pool configuration
    public int getMaxThreads() { return maxThreads; }
    public void setMaxThreads(int threads) { this.maxThreads = Math.max(1, threads); }
    
    public int getCoreThreads() { return coreThreads; }
    public void setCoreThreads(int threads) { this.coreThreads = Math.max(1, Math.min(threads, maxThreads)); }
    
    public long getThreadKeepAliveMs() { return threadKeepAliveMs; }
    public void setThreadKeepAliveMs(long ms) { this.threadKeepAliveMs = Math.max(1000, ms); }
    
    // Performance monitoring
    public boolean isPerformanceMonitoringEnabled() { return performanceMonitoringEnabled; }
    public void setPerformanceMonitoringEnabled(boolean enabled) { this.performanceMonitoringEnabled = enabled; }
    
    public long getPerformanceReportIntervalMs() { return performanceReportIntervalMs; }
    public void setPerformanceReportIntervalMs(long ms) { this.performanceReportIntervalMs = Math.max(1000, ms); }
    
    /**
     * Check if async operations are allowed based on global mode
     */
    private boolean isAsyncAllowed() {
        return globalMode != RenderExecutionMode.SYNC;
    }
    
    /**
     * Determine if async should be used for a given workload size
     */
    public boolean shouldUseAsync(int workloadSize, int threshold) {
        return switch (globalMode) {
            case SYNC -> false;
            case ASYNC -> true;
            case ADAPTIVE, HYBRID -> workloadSize >= threshold;
        };
    }
    
    /**
     * Create an immutable snapshot of current configuration
     */
    public AsyncRenderConfig snapshot() {
        return new AsyncRenderConfig(this);
    }
    
    @Override
    public String toString() {
        return String.format("AsyncRenderConfig[mode=%s, tick=%s, uniform=%s, vertex=%s, instance=%s, threads=%d/%d]",
            globalMode, asyncTickEnabled, asyncUniformCollectionEnabled, 
            asyncVertexFillEnabled, asyncInstanceUpdateEnabled, coreThreads, maxThreads);
    }
}
