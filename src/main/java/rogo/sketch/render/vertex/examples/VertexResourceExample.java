package rogo.sketch.render.vertex.examples;

import org.lwjgl.opengl.GL11;
import rogo.sketch.render.*;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataType;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.resource.RenderTarget;
import rogo.sketch.render.shader.uniform.DataType;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.render.state.RenderStateRegistry;
import rogo.sketch.util.Identifier;

/**
 * Example demonstrating the mixed vertex resource management system
 */
public class VertexResourceExample {
    
    public static void setupMixedRenderingExample() {
        // Initialize the render state registry
        RenderStateRegistry.init();
        
        // Create data formats
        DataFormat particleFormat = new DataFormat(
            new DataElement("position", DataType.VEC3, 0, false),     // position
            new DataElement(1, DataType.FLOAT, 4, false, 12),    // color
            new DataElement(2, DataType.FLOAT, 2, false, 28)     // UV
        );
        
        DataFormat cubeStaticFormat = new DataFormat(
            new DataElement(0, DataType.FLOAT, 3, false, 0),     // position
            new DataElement(1, DataType.FLOAT, 4, false, 12)     // color
        );
        
        DataFormat cubeInstanceFormat = new DataFormat(
            new DataElement(3, DataType.FLOAT, 3, false, 0),     // instance position
            new DataElement(4, DataType.FLOAT, 1, false, 12)     // instance scale
        );
        
        // Create render settings for different types
        RenderSetting particleSetting = createRenderSetting(
            RenderParameter.createDynamic(particleFormat, GL11.GL_TRIANGLES)
        );
        
        RenderSetting staticMeshSetting = createRenderSetting(
            RenderParameter.createInstanceStatic(cubeStaticFormat, GL11.GL_TRIANGLES)
        );
        
        RenderSetting instancedSetting = createRenderSetting(
            RenderParameter.createInstanced(cubeStaticFormat, GL11.GL_TRIANGLES)
        );
        
        // Create graphics pipeline
        GraphicsPipeline<RenderContext> pipeline = new GraphicsPipeline<>(false, createDummyContext());
        
        // Create and register stage
        GraphicsStage mainStage = new GraphicsStage(
            Identifier.of("main_render"),
            null, // No requirements
            100   // Priority
        );
        pipeline.registerStage(mainStage);
        
        // Create different types of graphics instances
        
        // 1. Batched particles (shared dynamic resource)
        for (int i = 0; i < 100; i++) {
            BatchedParticle particle = new BatchedParticle(
                Identifier.of("particle_" + i),
                (float) (Math.random() * 20 - 10), // x
                (float) (Math.random() * 20 - 10), // y
                (float) (Math.random() * 20 - 10), // z
                0.5f // size
            );
            pipeline.addGraphInstance(mainStage.getIdentifier(), particle, particleSetting);
        }
        
        // 2. Static mesh instances (instance-owned static resources)
        for (int i = 0; i < 10; i++) {
            float[] meshData = generateMeshData();
            StaticMeshInstance staticMesh = new StaticMeshInstance(
                Identifier.of("static_mesh_" + i),
                cubeStaticFormat,
                meshData
            );
            pipeline.addGraphInstance(mainStage.getIdentifier(), staticMesh, staticMeshSetting);
        }
        
        // 3. Instanced cubes (instance-owned instanced resources)
        InstancedCube instancedCubes = new InstancedCube(
            Identifier.of("instanced_cubes"),
            cubeStaticFormat,
            cubeInstanceFormat
        );
        
        // Add multiple instances to the same instanced cube
        for (int i = 0; i < 50; i++) {
            instancedCubes.addInstance(
                (float) (Math.random() * 20 - 10), // x
                (float) (Math.random() * 20 - 10), // y
                (float) (Math.random() * 20 - 10), // z
                (float) (Math.random() * 2 + 0.5)  // scale
            );
        }
        
        pipeline.addGraphInstance(mainStage.getIdentifier(), instancedCubes, instancedSetting);
        
        // Render the pipeline
        System.out.println("Rendering mixed vertex resource example...");
        System.out.println("- 100 batched particles (shared dynamic resource)");
        System.out.println("- 10 static meshes (instance-owned static resources)");
        System.out.println("- 1 instanced cube group with 50 instances (instance-owned instanced resource)");
        
        // This would render all three types appropriately:
        // 1. Particles batched together in shared vertex buffer
        // 2. Static meshes rendered individually with their own vertex buffers
        // 3. Instanced cubes rendered in one draw call with instancing
        pipeline.renderAllStages();
    }
    
    private static RenderSetting createRenderSetting(RenderParameter renderParameter) {
        return new RenderSetting(
            RenderStateRegistry.createDefaultFullRenderState(),
            new ResourceBinding(),
            null, // No render target for this example
            renderParameter
        );
    }
    
    private static RenderContext createDummyContext() {
        // This would be your actual render context implementation
        return new RenderContext() {
            @Override
            public void preStage(Identifier stageId) {
                System.out.println("Pre-stage: " + stageId);
            }
            
            @Override
            public void postStage(Identifier stageId) {
                System.out.println("Post-stage: " + stageId);
            }
            
            // Implement other required methods...
        };
    }
    
    private static float[] generateMeshData() {
        // Generate simple triangle mesh data
        return new float[] {
            // Triangle vertices
            0.0f,  1.0f, 0.0f,  // Top
           -1.0f, -1.0f, 0.0f,  // Bottom left
            1.0f, -1.0f, 0.0f   // Bottom right
        };
    }
}