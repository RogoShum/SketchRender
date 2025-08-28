package rogo.example;

import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.data.DataType;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.filler.VertexFillerManager;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertex.DrawMode;
import rogo.sketch.render.resource.buffer.VertexResource;

/**
 * Example demonstrating the new modular design where VertexFiller and VertexResource
 * are separated and managed through VertexFillerManager
 */
public class ModularVertexFillerExample {
    
    public static void main(String[] args) {
        System.out.println("=== Modular VertexFiller Design Demo ===");
        
        demonstrateModularDesign();
        demonstrateFillerReuse();
        demonstrateQuadTessellation();
    }
    
    /**
     * Demonstrate the new modular design pattern
     */
    private static void demonstrateModularDesign() {
        System.out.println("\n1. Modular Design Pattern:");
        
        // Create data format using builder
        DataFormat format = DataFormat.builder("quad_format")
            .add("pos", DataType.VEC3)
            .add("color", DataType.VEC4)
            .build();
        
        // Create render parameter
        RenderParameter parameter = RenderParameter.create(
            format,
            PrimitiveType.QUADS,
            Usage.STATIC_DRAW,  // usage pattern
            false               // disable sorting
        );
        
        // Get VertexFiller from manager
        VertexFillerManager manager = VertexFillerManager.getInstance();
        VertexFiller filler = manager.getOrCreateVertexFiller(parameter);
        
        // Create VertexResource separately
        VertexResource resource = new VertexResource(
            format,           // static format
            null,            // no dynamic format
            DrawMode.NORMAL, // draw mode
            PrimitiveType.QUADS, // primitive type
            Usage.STATIC_DRAW    // usage
        );
        
        try {
            // Fill vertex data (advanceElement auto-calls nextVertex after each complete vertex)
            filler.vec3f(-0.5f, 0.5f, 0.0f).vec4f(1.0f, 0.0f, 0.0f, 1.0f)     // Top-left (auto nextVertex)
                  .vec3f(0.5f, 0.5f, 0.0f).vec4f(0.0f, 1.0f, 0.0f, 1.0f)      // Top-right (auto nextVertex)
                  .vec3f(0.5f, -0.5f, 0.0f).vec4f(0.0f, 0.0f, 1.0f, 1.0f)     // Bottom-right (auto nextVertex)
                  .vec3f(-0.5f, -0.5f, 0.0f).vec4f(1.0f, 1.0f, 0.0f, 1.0f)    // Bottom-left (no more vertices)
                  .end();
            
            // Upload to GPU through VertexResource
            resource.uploadFromVertexFiller(filler);
            
            System.out.println("  [SUCCESS] Successfully created and uploaded quad data");
            System.out.println("    - Vertex count: " + filler.getVertexCount());
            System.out.println("    - Index count: " + filler.getIndexCount());
            System.out.println("    - Uses index buffer: " + filler.isUsingIndexBuffer());
            
        } catch (Exception e) {
            System.err.println("  [ERROR] Error: " + e.getMessage());
        } finally {
            // Reset filler for reuse (don't dispose - it's managed)
            manager.resetFiller(parameter);
            resource.dispose();
        }
    }
    
    /**
     * Demonstrate filler reuse through manager
     */
    private static void demonstrateFillerReuse() {
        System.out.println("\n2. Filler Reuse Pattern:");
        
        DataFormat format = DataFormat.builder("triangle_format")
            .add("pos", DataType.VEC3)
            .add("uv", DataType.VEC2)
            .build();
        
        RenderParameter parameter = RenderParameter.create(
            format,
            PrimitiveType.TRIANGLES,
            Usage.DYNAMIC_DRAW,  // dynamic for demonstration
            false
        );
        
        VertexFillerManager manager = VertexFillerManager.getInstance();
        
        // First use
        VertexFiller filler1 = manager.getOrCreateVertexFiller(parameter);
        System.out.println("  First retrieval: " + (filler1 != null ? "SUCCESS" : "FAILED"));
        
        // Second use - should return the same instance
        VertexFiller filler2 = manager.getOrCreateVertexFiller(parameter);
        System.out.println("  Second retrieval: " + (filler2 == filler1 ? "REUSED SAME INSTANCE" : "NEW INSTANCE"));
        
        // Check cache stats
        System.out.println("  " + manager.getCacheStats());
    }
    
    /**
     * Demonstrate quad tessellation with the new design
     */
    private static void demonstrateQuadTessellation() {
        System.out.println("\n3. Quad Tessellation with New Design:");
        
        DataFormat format = DataFormat.builder("textured_quad")
            .add("pos", DataType.VEC3)
            .add("uv", DataType.VEC2)
            .add("color", DataType.VEC4)
            .build();
        
        RenderParameter parameter = RenderParameter.create(
            format,
            PrimitiveType.QUADS,
            Usage.STREAM_DRAW,   // stream for single-use quad
            false
        );
        
        VertexFillerManager manager = VertexFillerManager.getInstance();
        VertexFiller filler = manager.getOrCreateVertexFiller(parameter);
        
        try {
            // Fill a textured quad (auto-advance after each complete vertex)
            filler.vec3f(-1.0f, 1.0f, 0.0f).vec2f(0.0f, 1.0f).vec4f(1.0f, 1.0f, 1.0f, 1.0f)     // TL (auto nextVertex)
                  .vec3f(1.0f, 1.0f, 0.0f).vec2f(1.0f, 1.0f).vec4f(1.0f, 1.0f, 1.0f, 1.0f)      // TR (auto nextVertex)
                  .vec3f(1.0f, -1.0f, 0.0f).vec2f(1.0f, 0.0f).vec4f(1.0f, 1.0f, 1.0f, 1.0f)     // BR (auto nextVertex)
                  .vec3f(-1.0f, -1.0f, 0.0f).vec2f(0.0f, 0.0f).vec4f(1.0f, 1.0f, 1.0f, 1.0f)    // BL (final vertex)
                  .end();
            
            System.out.println("  [SUCCESS] Quad tessellation completed:");
            System.out.println("    - Input vertices: " + filler.getVertexCount());
            System.out.println("    - Output indices: " + filler.getIndexCount() + " (2 triangles)");
            System.out.println("    - Primitive type: " + filler.getPrimitiveType());
            System.out.println("    - Index buffer enabled: " + filler.isUsingIndexBuffer());
            
        } catch (Exception e) {
            System.err.println("  [ERROR] Tessellation error: " + e.getMessage());
        } finally {
            manager.resetFiller(parameter);
        }
        
        System.out.println("\n=== Modular design successfully demonstrated! ===");
        System.out.println("Key benefits:");
        System.out.println("- VertexFiller and VertexResource are now separate modules");
        System.out.println("- VertexFillerManager provides global caching and reuse");
        System.out.println("- Index buffer management moved to VertexResource");
        System.out.println("- Clean separation of concerns: filling vs rendering");
    }
}
