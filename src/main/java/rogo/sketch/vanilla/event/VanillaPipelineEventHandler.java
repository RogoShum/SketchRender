package rogo.sketch.vanilla.event;

import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.event.bridge.IEventBusImplementation;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.MinecraftRenderStages;

/**
 * Event handler for vanilla Minecraft pipeline initialization
 * Uses the existing EventBusBridge system
 */
public class VanillaPipelineEventHandler {
    private static boolean registered = false;
    
    /**
     * Register the event handler
     */
    public static void register() {
        if (registered) {
            return;
        }
        
        EventBusBridge.subscribe(
            GraphicsPipelineInitEvent.class,
            VanillaPipelineEventHandler::onPipelineInit
        );
        
        registered = true;
    }
    
    /**
     * Handle pipeline initialization events
     */
    private static void onPipelineInit(GraphicsPipelineInitEvent<?> event) {
        if (!(event.getPipeline() instanceof McGraphicsPipeline mcPipeline)) {
            return; // Only handle MC pipelines
        }
        
        switch (event.getPhase()) {
            case EARLY -> {
                // Register vanilla stages first
                MinecraftRenderStages.registerVanillaStages(mcPipeline);
                System.out.println("Registered vanilla render stages during early init");
            }
            case NORMAL -> {
                // Register extra stages that might be added by mods
                MinecraftRenderStages.registerExtraStages(mcPipeline);
                System.out.println("Registered extra render stages during normal init");
            }
            case LATE -> {
                // Handle any late initialization tasks
                System.out.println("Processing late pipeline initialization");
                
                // Example: Validate stage ordering
                if (mcPipeline.getPendingStages().size() > 0) {
                    System.out.println("Warning: " + mcPipeline.getPendingStages().size() + " stages are still pending");
                }
            }
        }
    }
}
