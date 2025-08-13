package rogo.example;

import org.lwjgl.opengl.GL11;
import rogo.sketch.render.data.filler.ByteBufferFiller;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.DataType;
import rogo.sketch.render.vertex.VertexResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating the optimized VertexResource with buffer reuse
 * to avoid memory allocation overhead during high-frequency rendering.
 */
public class VertexResourceOptimizationExample {

    public static void runOptimizationExample() {
        // Create a vertex format for position + color + UV
        List<DataElement> elements = new ArrayList<>();
        elements.add(new DataElement("position", DataType.VEC3, 0, false));  // xyz position
        elements.add(new DataElement("color", DataType.VEC4, 12, false));     // rgba color
        elements.add(new DataElement("uv", DataType.VEC2, 28, false));        // uv texture coordinates

        DataFormat format = new DataFormat("test", elements);

        // Create optimized vertex resource
        VertexResource vertexResource = VertexResource.createDynamic(format, GL11.GL_TRIANGLES);

        // Enable buffer reuse for better performance (enabled by default)
        vertexResource.setBufferReuseEnabled(true);

        // Pre-allocate buffer for expected vertex count to avoid reallocations
        vertexResource.preAllocateStaticBuffer(1024); // Allocate for 1024 vertices

        System.out.println("=== VertexResource Optimization Example ===");
        System.out.println("Buffer reuse enabled: " + vertexResource.isBufferReuseEnabled());
        System.out.println("Initial buffer capacity: " + vertexResource.getStaticBufferCapacity() + " vertices");

        // Simulate multiple frames of rendering
        for (int frame = 0; frame < 5; frame++) {
            System.out.println("\n--- Frame " + frame + " ---");
            renderFrameOptimized(vertexResource);
        }

        // Demonstrate different filling approaches
        System.out.println("\n=== Comparing Different Approaches ===");

        // 1. Optimized reusable approach (recommended)
        demonstrateReusableApproach(vertexResource);

        // 2. Direct buffer approach (for maximum control)
        demonstrateDirectBufferApproach(vertexResource);

        // 3. Legacy approach (not recommended for high frequency)
        demonstrateLegacyApproach(vertexResource);

        // Clean up
        vertexResource.dispose();
        System.out.println("\nVertexResource disposed.");
    }

    /**
     * Render a frame using the optimized reusable buffer approach
     */
    private static void renderFrameOptimized(VertexResource vertexResource) {
        // Use reusable filler - no memory allocation!
        VertexFiller filler = vertexResource.beginFillReuse();

        // Add some example vertices
        addExampleVertices(filler);

        System.out.println("Vertices added: " + filler.getVertexCount());
        System.out.println("Indices added: " + filler.getIndexCount());

        // Upload to GPU - the filler is NOT disposed, ready for reuse
        vertexResource.endFill(filler);

        System.out.println("Frame rendered with reusable filler");
    }

    /**
     * Demonstrate the optimized reusable approach
     */
    private static void demonstrateReusableApproach(VertexResource vertexResource) {
        System.out.println("\n1. Reusable Approach (Optimized):");

        long startTime = System.nanoTime();

        for (int i = 0; i < 100; i++) {
            VertexFiller filler = vertexResource.beginFillReuse();
            addExampleVertices(filler);
            vertexResource.endFill(filler);
        }

        long endTime = System.nanoTime();
        System.out.println("100 iterations took: " + (endTime - startTime) / 1_000_000 + " ms");
        System.out.println("✅ Minimal memory allocations - reuses same buffer");
    }

    /**
     * Demonstrate the direct buffer approach
     */
    private static void demonstrateDirectBufferApproach(VertexResource vertexResource) {
        System.out.println("\n2. Direct Buffer Approach (Maximum Control):");

        long startTime = System.nanoTime();

        for (int i = 0; i < 100; i++) {
            ByteBufferFiller filler = vertexResource.beginFillDirect();

            // Direct buffer access - write vertices manually
            filler.vertex(0).element(0).floatValue(0.0f).floatValue(0.0f).floatValue(0.0f); // position
            filler.element(1).floatValue(1.0f).floatValue(0.0f).floatValue(0.0f).floatValue(1.0f); // color
            filler.element(2).floatValue(0.0f).floatValue(0.0f); // uv

            filler.vertex(1).element(0).floatValue(1.0f).floatValue(0.0f).floatValue(0.0f);
            filler.element(1).floatValue(0.0f).floatValue(1.0f).floatValue(0.0f).floatValue(1.0f);
            filler.element(2).floatValue(1.0f).floatValue(0.0f);

            filler.vertex(2).element(0).floatValue(0.5f).floatValue(1.0f).floatValue(0.0f);
            filler.element(1).floatValue(0.0f).floatValue(0.0f).floatValue(1.0f).floatValue(1.0f);
            filler.element(2).floatValue(0.5f).floatValue(1.0f);

            vertexResource.endFillDirect(filler, 3);
        }

        long endTime = System.nanoTime();
        System.out.println("100 iterations took: " + (endTime - startTime) / 1_000_000 + " ms");
        System.out.println("✅ Direct buffer control - no overhead from VertexFiller");
    }

    /**
     * Demonstrate the legacy approach (not recommended for high frequency)
     */
    private static void demonstrateLegacyApproach(VertexResource vertexResource) {
        System.out.println("\n3. Legacy Approach (Not Recommended):");

        // Temporarily disable buffer reuse to show legacy behavior
        vertexResource.setBufferReuseEnabled(false);

        long startTime = System.nanoTime();

        for (int i = 0; i < 100; i++) {
            VertexFiller filler = vertexResource.beginFill(); // This creates new buffers each time
            addExampleVertices(filler);
            vertexResource.endFill(filler); // This disposes the filler
        }

        long endTime = System.nanoTime();
        System.out.println("100 iterations took: " + (endTime - startTime) / 1_000_000 + " ms");
        System.out.println("❌ High memory allocation overhead - creates new buffers each time");

        // Re-enable buffer reuse
        vertexResource.setBufferReuseEnabled(true);
    }

    /**
     * Add example vertices to a VertexFiller
     */
    private static void addExampleVertices(VertexFiller filler) {
        // Triangle vertices
        filler.vertex(0).element(0).floatValue(0.0f).floatValue(0.0f).floatValue(0.0f); // position
        filler.element(1).floatValue(1.0f).floatValue(0.0f).floatValue(0.0f).floatValue(1.0f); // color (red)
        filler.element(2).floatValue(0.0f).floatValue(0.0f); // uv

        filler.vertex(1).element(0).floatValue(1.0f).floatValue(0.0f).floatValue(0.0f); // position
        filler.element(1).floatValue(0.0f).floatValue(1.0f).floatValue(0.0f).floatValue(1.0f); // color (green)
        filler.element(2).floatValue(1.0f).floatValue(0.0f); // uv

        filler.vertex(2).element(0).floatValue(0.5f).floatValue(1.0f).floatValue(0.0f); // position
        filler.element(1).floatValue(0.0f).floatValue(0.0f).floatValue(1.0f).floatValue(1.0f); // color (blue)
        filler.element(2).floatValue(0.5f).floatValue(1.0f); // uv

        // Add indices for the triangle
        filler.triangle(0, 1, 2);
    }

    public static void main(String[] args) {
        runOptimizationExample();
    }
}
