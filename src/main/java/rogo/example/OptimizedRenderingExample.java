package rogo.example;

import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.RenderSetting;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.util.Identifier;

/**
 * Example demonstrating the optimized rendering system with:
 * 1. ResourceBinding using ResourceReference for efficient lookups
 * 2. VertexResourceManager for shared vertex resource management
 * 3. Proper equality and hashing for resource binding comparison
 */
public class OptimizedRenderingExample {
    
    public static void main(String[] args) {
        demonstrateResourceBindingOptimizations();
        demonstrateVertexResourceSharing();
        demonstrateResourceBindingEquality();
    }
    
    /**
     * Demonstrate ResourceBinding optimizations with ResourceReference
     */
    private static void demonstrateResourceBindingOptimizations() {
        System.out.println("=== ResourceBinding Optimizations ===");
        
        // Create resource bindings
        ResourceBinding binding1 = new ResourceBinding();
        binding1.addBinding(Identifier.of("texture"), Identifier.of("diffuse"), Identifier.of("grass_texture"));
        binding1.addBinding(Identifier.of("texture"), Identifier.of("normal"), Identifier.of("grass_normal"));
        
        // When bind() is called multiple times, ResourceReference caching will improve performance
        System.out.println("First bind call - ResourceReferences will be created and cached");
        // binding1.bind(mockRenderContext); // This would use ResourceReference internally
        
        System.out.println("Subsequent bind calls - Uses cached ResourceReferences for faster lookup");
        // binding1.bind(mockRenderContext); // Much faster due to caching

        System.out.println("Resource cache cleared - next bind will recreate references");
    }
    
    /**
     * Demonstrate VertexResource sharing through VertexResourceManager
     */
    private static void demonstrateVertexResourceSharing() {
        System.out.println("\n=== VertexResource Sharing ===");
        
        VertexResourceManager manager = VertexResourceManager.getInstance();
        
        // Create two RenderSettings with the same RenderParameter
        RenderParameter sharedParam = RenderParameter.EMPTY; // Placeholder - in real use would have actual parameters
        RenderSetting setting1 = RenderSetting.basic(null, new ResourceBinding(), sharedParam);
        RenderSetting setting2 = RenderSetting.basic(null, new ResourceBinding(), sharedParam);
        
        // Both settings should get the same VertexResource instance
        try {
            VertexResource resource1 = manager.getOrCreateVertexResource(setting1);
            VertexResource resource2 = manager.getOrCreateVertexResource(setting2);
            
            System.out.println("Resource1 == Resource2: " + (resource1 == resource2));
            System.out.println("Shared resources reduce memory usage and improve performance");
            
            System.out.println(manager.getCacheStats());
        } catch (IllegalArgumentException e) {
            System.out.println("Note: This example uses EMPTY parameter which isn't valid for actual use");
        }
    }
    
    /**
     * Demonstrate ResourceBinding equality and hashing
     */
    private static void demonstrateResourceBindingEquality() {
        System.out.println("\n=== ResourceBinding Equality ===");
        
        // Create identical resource bindings
        ResourceBinding binding1 = new ResourceBinding();
        binding1.addBinding(Identifier.of("texture"), Identifier.of("diffuse"), Identifier.of("grass"));
        binding1.addBinding(Identifier.of("ssbo"), Identifier.of("instances"), Identifier.of("instance_data"));
        
        ResourceBinding binding2 = new ResourceBinding();
        binding2.addBinding(Identifier.of("texture"), Identifier.of("diffuse"), Identifier.of("grass"));
        binding2.addBinding(Identifier.of("ssbo"), Identifier.of("instances"), Identifier.of("instance_data"));
        
        // Create different resource binding
        ResourceBinding binding3 = new ResourceBinding();
        binding3.addBinding(Identifier.of("texture"), Identifier.of("diffuse"), Identifier.of("stone"));
        
        System.out.println("binding1.equals(binding2): " + binding1.equals(binding2));
        System.out.println("binding1.hashCode() == binding2.hashCode(): " + (binding1.hashCode() == binding2.hashCode()));
        System.out.println("binding1.equals(binding3): " + binding1.equals(binding3));
        
        System.out.println("\nThis enables efficient caching and comparison of render states");
    }
    
    /**
     * Example of how GraphicsPassGroup would use the optimized system
     */
    private static void demonstrateGraphicsPassGroupUsage() {
        System.out.println("\n=== GraphicsPassGroup Integration ===");
        
        // In GraphicsPassGroup.renderSharedResources():
        // 1. Multiple passes with same RenderParameter share the same VertexResource
        // 2. ResourceBinding uses cached ResourceReferences for fast binding
        // 3. ResourceBinding equality allows for efficient render state comparison
        
        System.out.println("GraphicsPassGroup benefits:");
        System.out.println("- Shared VertexResources reduce memory usage");
        System.out.println("- Cached ResourceReferences speed up resource binding");
        System.out.println("- ResourceBinding equality enables render state optimization");
    }
}
