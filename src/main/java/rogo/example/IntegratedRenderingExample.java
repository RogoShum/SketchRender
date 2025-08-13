package rogo.example;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.RenderSetting;
import rogo.sketch.render.async.AsyncRenderConfig;
import rogo.sketch.render.async.AsyncRenderManager;
import rogo.sketch.render.async.RenderExecutionMode;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.render.pool.PoolableGraphicsInstance;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.McGraphicsPipeline;

/**
 * Example demonstrating the integrated use of instance pooling, async rendering, and event system
 */
public class IntegratedRenderingExample {
    
    public static void demonstrateIntegratedWorkflow() {
        System.out.println("=== Integrated Rendering Workflow ===");
        
        setupInstancePools();
        configureAsyncRendering();
        registerEventHandlers();
        demonstratePipelineUsage();
    }
    
    /**
     * Setup instance pools for different graphics instance types
     */
    private static void setupInstancePools() {
        System.out.println("Setting up instance pools...");
        
        InstancePoolManager poolManager = InstancePoolManager.getInstance();
        
        // Enable pooling
        poolManager.setPoolingEnabled(true);
        
        // Register type-based pool for simple instances
        poolManager.registerTypePool(
            ExampleGraphicsInstance.class,
            ExampleGraphicsInstance::new,
            100
        );
        
        // Register named pools for specific use cases
        poolManager.registerNamedPool(
            Identifier.of("particles"),
            ExamplePoolableInstance::new,
            200
        );
        
        poolManager.registerNamedPool(
            Identifier.of("effects"),
            ExamplePoolableInstance::new,
            50
        );
        
        System.out.println("Instance pools configured: " + poolManager.getStats());
    }
    
    /**
     * Configure async rendering settings
     */
    private static void configureAsyncRendering() {
        System.out.println("Configuring async rendering...");
        
        AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
        AsyncRenderConfig config = asyncManager.getConfig();
        
        // Set adaptive mode for automatic async/sync selection
        config.setGlobalMode(RenderExecutionMode.ADAPTIVE);
        
        // Configure individual async features
        config.setAsyncTickEnabled(true);
        config.setAsyncVertexFillEnabled(true);
        config.setAsyncUniformCollectionEnabled(true);
        config.setAsyncInstanceUpdateEnabled(true);
        
        // Set thresholds for when to use async
        config.setAsyncThreshold(16);
        config.setVertexFillThreshold(12);
        config.setUniformCollectionThreshold(20);
        config.setInstanceUpdateThreshold(15);
        
        // Configure thread pool
        config.setMaxThreads(Math.min(8, Runtime.getRuntime().availableProcessors()));
        config.setCoreThreads(Math.max(2, config.getMaxThreads() / 2));
        
        // Enable performance monitoring
        config.setPerformanceMonitoringEnabled(true);
        config.setPerformanceReportIntervalMs(10000); // Report every 10 seconds
        
        // Apply configuration
        asyncManager.updateConfig(config);
        
        System.out.println("Async rendering configured: " + config);
    }
    
    /**
     * Register event handlers for pipeline events
     */
    private static void registerEventHandlers() {
        System.out.println("Registering event handlers...");
        
        // Register handler for pipeline initialization
        EventBusBridge.subscribe(
            GraphicsPipelineInitEvent.class,
            IntegratedRenderingExample::onPipelineInit
        );
        
        System.out.println("Event handlers registered");
    }
    
    /**
     * Handle pipeline initialization events
     */
    private static void onPipelineInit(GraphicsPipelineInitEvent<?> event) {
        System.out.println("Pipeline init event: " + event.getPhase());
        
        if (event.getPipeline() instanceof McGraphicsPipeline pipeline) {
            switch (event.getPhase()) {
                case EARLY -> {
                    // Early initialization - core setup
                    System.out.println("Early init: Setting up core pipeline features");
                }
                case NORMAL -> {
                    // Normal initialization - add custom instances
                    setupCustomInstances(pipeline);
                }
                case LATE -> {
                    // Late initialization - finalization
                    System.out.println("Late init: Pipeline statistics: " + pipeline.getStats());
                }
            }
        }
    }
    
    /**
     * Setup custom instances during pipeline initialization
     */
    private static void setupCustomInstances(McGraphicsPipeline pipeline) {
        System.out.println("Setting up custom instances...");
        
        // Create render setting
        RenderSetting renderSetting = RenderSetting.basic(
            null, // No shader overrides
            new ResourceBinding(), // Empty resource binding for example
            RenderParameter.EMPTY // Simple render parameter
        );
        
        try {
            // Add pooled instances to different stages
            pipeline.addPooledGraphInstance(
                Identifier.of("vanilla_entities"),
                ExampleGraphicsInstance.class,
                renderSetting
            );
            
            pipeline.addNamedPoolGraphInstance(
                Identifier.of("vanilla_particle"),
                Identifier.of("particles"),
                renderSetting
            );
            
            pipeline.addNamedPoolGraphInstance(
                Identifier.of("vanilla_particle"),
                Identifier.of("effects"),
                renderSetting
            );
            
            System.out.println("Custom instances added to pipeline");
        } catch (Exception e) {
            System.err.println("Failed to add custom instances: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrate pipeline usage with the new features
     */
    private static void demonstratePipelineUsage() {
        System.out.println("Demonstrating pipeline usage...");
        
        // Create and initialize pipeline
        McGraphicsPipeline pipeline = new McGraphicsPipeline(true);
        pipeline.initialize(); // This will trigger our event handlers
        
        // Simulate some rendering cycles
        for (int i = 0; i < 5; i++) {
            System.out.println("Render cycle " + (i + 1));
            
            // Tick all stages (will use async if beneficial)
            pipeline.tickAllStages();
            
            // Cleanup discarded instances (returns them to pools)
            pipeline.cleanupInstances();
            
            // Simulate some time passing
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Print final statistics
        System.out.println("Final pipeline stats: " + pipeline.getStats());
        System.out.println("Pool manager stats: " + pipeline.getPoolManager().getStats());
        System.out.println("Async manager stats: " + pipeline.getAsyncManager().getPerformanceStats());
    }
    
    // Example implementations
    
    private static class ExampleGraphicsInstance implements GraphicsInstance {
        private final Identifier id = Identifier.of("example_" + System.nanoTime());
        
        @Override
        public Identifier getIdentifier() { return id; }
        
        @Override
        public boolean shouldTick() { return true; }
        
        @Override
        public <C extends RenderContext> void tick(C context) {
            // Example tick logic
        }
        
        @Override
        public boolean shouldDiscard() {
            // Randomly discard some instances for demonstration
            return Math.random() < 0.1; // 10% chance
        }
        
        @Override
        public boolean shouldRender() { return true; }
        
        @Override
        public void endDraw() {
            // Example end draw logic
        }
    }
    
    private static class ExamplePoolableInstance implements PoolableGraphicsInstance {
        private Identifier id = Identifier.of("poolable_" + System.nanoTime());
        private Identifier poolId = Identifier.of("particles");
        private Object[] params;
        
        @Override
        public Identifier getIdentifier() { return id; }
        
        @Override
        public Identifier getPoolIdentifier() { return poolId; }
        
        @Override
        public void configure(Object... params) {
            this.params = params;
        }
        
        @Override
        public void reset() {
            this.params = null;
            this.id = Identifier.of("poolable_" + System.nanoTime());
        }
        
        @Override
        public boolean shouldTick() { return params != null; }
        
        @Override
        public <C extends RenderContext> void tick(C context) {
            // Example tick with parameters
        }
        
        @Override
        public boolean shouldDiscard() {
            // Randomly discard some instances for demonstration
            return Math.random() < 0.05; // 5% chance
        }
        
        @Override
        public boolean shouldRender() { return params != null; }
        
        @Override
        public void endDraw() {
            // Example end draw logic
        }
    }
}
