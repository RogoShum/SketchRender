package rogo.example;

import org.lwjgl.opengl.GL11;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResource;

/**
 * Example demonstrating the separated vertex resource management architecture.
 * VertexResource handles resource management, VertexRenderer handles drawing.
 */
public class VertexResourceExample {

    public static void basicSeparatedExample() {
        // Create a basic vertex format
        DataFormat format = DataFormat.builder("BasicVertex")
                .vec3Attribute("position")
                .vec4Attribute("color")
                .build();

        // Create vertex resource (no rendering logic)
        VertexResource resource = VertexResource.createStatic(format, GL11.GL_TRIANGLES);

        // Fill vertex data using VertexFiller
        VertexFiller filler = resource.beginFill();

        // Add triangle vertices - IndexBuffer is enabled by default
        filler.position(0.0f, 0.5f, 0.0f)
                .color(1.0f, 0.0f, 0.0f, 1.0f)
                .nextVertex();

        filler.position(-0.5f, -0.5f, 0.0f)
                .color(0.0f, 1.0f, 0.0f, 1.0f)
                .nextVertex();

        filler.position(0.5f, -0.5f, 0.0f)
                .color(0.0f, 0.0f, 1.0f, 1.0f);

        // Add indices for the triangle
        filler.triangle(0, 1, 2);

        // Finish filling - automatic upload to GPU
        resource.endFill();

        // External rendering using VertexRenderer
        VertexRenderer.render(resource);

        // Print statistics
        VertexRenderer.RenderStats stats = VertexRenderer.getRenderStats(resource);
        System.out.println("Render stats: " + stats);

        // Cleanup
        resource.dispose();
    }

    public static void indexedQuadExample() {
        DataFormat format = DataFormat.builder("IndexedVertex")
                .vec3Attribute("position")
                .vec4Attribute("color")
                .build();

        // Create resource - IndexBuffer enabled by default
        VertexResource resource = VertexResource.createStatic(format, GL11.GL_TRIANGLES);

        // Fill vertex data
        VertexFiller filler = resource.beginFill();

        // Define 4 vertices for a quad (instead of 6 for two triangles)
        filler.position(-0.5f, 0.5f, 0.0f).color(1, 0, 0, 1).nextVertex();  // 0
        filler.position(0.5f, 0.5f, 0.0f).color(0, 1, 0, 1).nextVertex();   // 1
        filler.position(0.5f, -0.5f, 0.0f).color(0, 0, 1, 1).nextVertex();  // 2
        filler.position(-0.5f, -0.5f, 0.0f).color(1, 1, 0, 1);              // 3

        // Create indices for two triangles forming a quad
        filler.quad(0, 1, 2, 3); // Generates: 0,1,2,2,3,0

        resource.endFill();

        // Render using external renderer
        VertexRenderer.render(resource);

        System.out.println("Vertex count: " + resource.getStaticVertexCount()); // 4
        System.out.println("Index count: " + resource.getIndexBuffer().getIndexCount()); // 6
        System.out.println("Has indices: " + resource.hasIndices()); // true

        resource.dispose();
    }

    public static void sortedTransparencyExample() {
        DataFormat format = DataFormat.builder("SortedVertex")
                .vec3Attribute("position")
                .vec4Attribute("color")
                .build();

        VertexResource resource = VertexResource.createStatic(format, GL11.GL_TRIANGLES);

        // Enable sorting for transparency
        VertexFiller filler = resource.beginFill()
                .enableSorting()
                .sortByDistanceToOrigin();

        // Add multiple quads at different depths
        for (int i = 0; i < 3; i++) {
            float z = i * 0.3f;
            int baseVertex = i * 4;

            // Add vertices for each quad
            filler.position(-0.3f, 0.3f, z).color(1, 0, 0, 0.7f).nextVertex();
            filler.position(0.3f, 0.3f, z).color(0, 1, 0, 0.7f).nextVertex();
            filler.position(0.3f, -0.3f, z).color(0, 0, 1, 0.7f).nextVertex();
            filler.position(-0.3f, -0.3f, z).color(1, 1, 0, 0.7f).nextVertex();

            filler.quad(baseVertex, baseVertex + 1, baseVertex + 2, baseVertex + 3);
        }

        resource.endFill();

        // Render sorted for proper transparency
        VertexRenderer.render(resource);

        System.out.println("Sorted " + resource.getStaticVertexCount() + " vertices for transparency");

        resource.dispose();
    }

    public static void instancedRenderingExample() {
        // Static mesh format
        DataFormat meshFormat = DataFormat.builder("Mesh")
                .vec3Attribute("position")
                .vec3Attribute("normal")
                .build();

        // Instance data format
        DataFormat instanceFormat = DataFormat.builder("Instance")
                .mat4Attribute("instanceMatrix")
                .vec4Attribute("instanceColor")
                .build();

        // Create hybrid resource for instanced rendering
        VertexResource resource = VertexResource.createInstanced(meshFormat, instanceFormat, GL11.GL_TRIANGLES);

        // Fill static mesh data
        VertexFiller meshFiller = resource.beginFill();

        // Triangle mesh
        meshFiller.position(0.0f, 0.5f, 0.0f).normal(0, 0, 1).nextVertex();
        meshFiller.position(-0.5f, -0.5f, 0.0f).normal(0, 0, 1).nextVertex();
        meshFiller.position(0.5f, -0.5f, 0.0f).normal(0, 0, 1);

        // Add triangle indices
        meshFiller.triangle(0, 1, 2);

        resource.endFill();

        // Add instance data
        for (int i = 0; i < 5; i++) {
            var instanceFiller = resource.addInstance();
            // Fill instance transformation and color data here
            // instanceFiller.mat4(transformMatrix).vec4f(color);
        }
        resource.endInstanceFill();

        // Render all instances
        VertexRenderer.render(resource, GL11.GL_TRIANGLES, GL11.GL_TRIANGLES);

        VertexRenderer.RenderStats stats = VertexRenderer.getRenderStats(resource);
        System.out.println("Instanced rendering: " + stats);
        System.out.println("Draw calls: " + stats.getDrawCallCount());

        resource.dispose();
    }

    public static void customRenderingExample() {
        DataFormat format = DataFormat.builder("CustomVertex")
                .vec3Attribute("position")
                .vec4Attribute("color")
                .build();

        VertexResource resource = VertexResource.createStatic(format, GL11.GL_LINE_STRIP);

        VertexFiller filler = resource.beginFill();

        // Create a line strip
        for (int i = 0; i < 10; i++) {
            float x = i * 0.1f - 0.5f;
            float y = (float) Math.sin(x * Math.PI * 2) * 0.3f;

            filler.position(x, y, 0.0f)
                    .color(i / 9.0f, 1.0f - i / 9.0f, 0.5f, 1.0f)
                    .nextVertex();
        }

        resource.endFill();

        // Custom rendering parameters
        VertexRenderer.render(resource, 0, resource.getStaticVertexCount());

        // Get detailed statistics
        VertexRenderer.RenderStats stats = VertexRenderer.getRenderStats(resource);
        System.out.println("Line rendering stats: " + stats);

        resource.dispose();
    }

    public static void resourceReuseExample() {
        DataFormat format = DataFormat.builder("ReuseVertex")
                .vec3Attribute("position")
                .vec4Attribute("color")
                .build();

        VertexResource resource = VertexResource.createStatic(format, GL11.GL_TRIANGLES);

        // First frame - triangle
        VertexFiller filler = resource.beginFill();
        filler.position(0, 0.5f, 0).color(1, 0, 0, 1).nextVertex();
        filler.position(-0.5f, -0.5f, 0).color(0, 1, 0, 1).nextVertex();
        filler.position(0.5f, -0.5f, 0).color(0, 0, 1, 1);
        filler.triangle(0, 1, 2);
        resource.endFill();

        VertexRenderer.render(resource);
        System.out.println("Frame 1: Triangle rendered");

        // Second frame - update to quad (resource reuse)
        filler = resource.beginFill();
        filler.position(-0.5f, 0.5f, 0).color(1, 0, 0, 1).nextVertex();
        filler.position(0.5f, 0.5f, 0).color(0, 1, 0, 1).nextVertex();
        filler.position(0.5f, -0.5f, 0).color(0, 0, 1, 1).nextVertex();
        filler.position(-0.5f, -0.5f, 0).color(1, 1, 0, 1);
        filler.quad(0, 1, 2, 3);
        resource.endFill();

        VertexRenderer.render(resource);
        System.out.println("Frame 2: Quad rendered");

        resource.dispose();
    }

    public static void performanceComparisonExample() {
        System.out.println("=== Architecture Comparison ===");

        DataFormat format = DataFormat.builder("PerfTest")
                .vec3Attribute("position")
                .vec4Attribute("color")
                .build();

        // New separated architecture
        VertexResource resource = VertexResource.createStatic(format, GL11.GL_TRIANGLES);

        long startTime = System.nanoTime();
        VertexFiller filler = resource.beginFill();
        filler.position(0, 0.5f, 0).color(1, 0, 0, 1).nextVertex();
        filler.position(-0.5f, -0.5f, 0).color(0, 1, 0, 1).nextVertex();
        filler.position(0.5f, -0.5f, 0).color(0, 0, 1, 1);
        filler.triangle(0, 1, 2);
        resource.endFill();

        VertexRenderer.render(resource);
        long separatedTime = System.nanoTime() - startTime;

        System.out.println("Separated architecture time: " + separatedTime + " ns");
        System.out.println("Vertex count: " + resource.getStaticVertexCount());
        System.out.println("Index buffer enabled: " + resource.hasIndices());
        System.out.println("Resource management: Separated from rendering");

        VertexRenderer.RenderStats stats = VertexRenderer.getRenderStats(resource);
        System.out.println("Render stats: " + stats);

        resource.dispose();
    }

    public static void main(String[] args) {
        System.out.println("=== Vertex Resource Architecture Examples ===");

        // These examples demonstrate the separated architecture
        System.out.println("1. Basic separated resource and renderer");
        // basicSeparatedExample();

        System.out.println("2. Indexed quad with automatic index buffer");
        // indexedQuadExample();

        System.out.println("3. Sorted transparency rendering");
        // sortedTransparencyExample();

        System.out.println("4. Instanced rendering");
        // instancedRenderingExample();

        System.out.println("5. Custom rendering parameters");
        // customRenderingExample();

        System.out.println("6. Resource reuse between frames");
        // resourceReuseExample();

        System.out.println("7. Performance comparison");
        performanceComparisonExample();

        System.out.println("\nArchitecture improvements:");
        System.out.println("✅ Separated resource management from rendering logic");
        System.out.println("✅ IndexBuffer enabled by default - no manual setup needed");
        System.out.println("✅ External VertexRenderer handles all drawing commands");
        System.out.println("✅ VertexResource focuses purely on data management");
        System.out.println("✅ Flexible rendering with custom parameters");
        System.out.println("✅ Clear separation of concerns");
        System.out.println("✅ Resource can be reused across multiple frames");
    }
} 