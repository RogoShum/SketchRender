package rogo.sketch.render.example;

import org.joml.Matrix4f;
import rogo.sketch.api.graphics.*;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.model.ModelMesh;
import rogo.sketch.render.vertex.InstancedVertexLayout;
import rogo.sketch.util.Identifier;

/**
 * Example implementation showing how to use the new instanced rendering system
 */
public class InstancedRenderingExample {

    /**
     * Example graphics instance that supports instanced rendering
     */
    public static class InstancedCube implements GraphicsInstance,
            RenderSettingProvider,
            ModelMeshProvider,
            MatrixProvider,
            InstancedLayoutProvider {

        private final Identifier identifier;
        private final ModelMesh cubeModel;
        private final InstancedVertexLayout instanceLayout;
        private final CubeInstance[] instances;
        private final Matrix4f baseTransform;

        public InstancedCube(Identifier id, ModelMesh cubeModel, CubeInstance[] instances) {
            this.identifier = id;
            this.cubeModel = cubeModel;
            this.instances = instances;
            this.instanceLayout = createInstanceLayout();
            this.baseTransform = new Matrix4f();
        }

        @Override
        public Identifier getIdentifier() {
            return identifier;
        }

        @Override
        public boolean shouldTick() {
            return false;
        }

        @Override
        public <C extends RenderContext> void tick(C context) {

        }

        @Override
        public boolean shouldDiscard() {
            return false;
        }

        @Override
        public boolean shouldRender() {
            return instances.length > 0;
        }

        @Override
        public <C extends RenderContext> void afterDraw(C context) {

        }

        @Override
        public <C extends RenderContext> RenderSetting getRenderSetting(C context) {
            // Return appropriate render setting for cubes
            return createCubeRenderSetting();
        }

        @Override
        public ModelMesh getModelMesh() {
            return cubeModel;
        }

        @Override
        public Matrix4f getMeshMatrix() {
            return new Matrix4f(baseTransform);
        }

        @Override
        public InstancedVertexLayout getInstancedVertexLayout() {
            return instanceLayout;
        }

        @Override
        public void fillInstanceVertexData(VertexFiller filler, int index) {
            // New index-based implementation - works in both sequential and indexed modes
            if (index < instances.length) {
                CubeInstance instance = instances[index];

                if (filler.supportsRandomAccess()) {
                    // 索引模式：直接写入指定vertex位置，适用于异步并行
                    // 使用便捷方法填充位置和颜色
                    filler.positionAt(index, instance.position[0], instance.position[1], instance.position[2]);
                    // Write scale using byte offset calculation
                    long scaleOffset = index * filler.getFormat().getStride() + 
                                     filler.getFormat().getElements().get(3).getOffset();
                    filler.putFloatAt(scaleOffset, instance.scale);
                    // Write color using byte offset calculation  
                    long colorOffset = index * filler.getFormat().getStride() +
                                     filler.getFormat().getElements().get(4).getOffset();
                    filler.putFloatAt(colorOffset, instance.color[0]);
                    filler.putFloatAt(colorOffset + Float.BYTES, instance.color[1]);
                    filler.putFloatAt(colorOffset + 2 * Float.BYTES, instance.color[2]);
                    filler.putFloatAt(colorOffset + 3 * Float.BYTES, instance.color[3]);
                } else {
                    // 顺序模式：按顺序填充当前vertex，适用于同步操作
                    filler.position(instance.position[0], instance.position[1], instance.position[2])
                          .putFloat(instance.scale);
                    filler.putVec4(instance.color[0], instance.color[1], instance.color[2], instance.color[3]);
                }
            } else {
                // Fill with default data if index is out of bounds
                if (filler.supportsRandomAccess()) {
                    filler.positionAt(index, 0.0f, 0.0f, 0.0f);
                    // Write default scale using byte offset
                    long scaleOffset = index * filler.getFormat().getStride() + 
                                     filler.getFormat().getElements().get(3).getOffset();
                    filler.putFloatAt(scaleOffset, 1.0f);
                    // Write default color using byte offset
                    long colorOffset = index * filler.getFormat().getStride() +
                                     filler.getFormat().getElements().get(4).getOffset();
                    filler.putFloatAt(colorOffset, 1.0f);
                    filler.putFloatAt(colorOffset + Float.BYTES, 1.0f);
                    filler.putFloatAt(colorOffset + 2 * Float.BYTES, 1.0f);
                    filler.putFloatAt(colorOffset + 3 * Float.BYTES, 1.0f);
                } else {
                    filler.position(0.0f, 0.0f, 0.0f)
                          .putFloat(1.0f)
                          .putVec4(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        }

        @Override
        public void fillInstanceVertexData(VertexFiller filler) {
            // Backward compatibility method - fill the first instance
            if (instances.length > 0) {
                CubeInstance instance = instances[0];
                filler.putFloat(instance.position[0])
                        .putFloat(instance.position[1])
                        .putFloat(instance.position[2])
                        .putFloat(instance.scale)
                        .putFloat(instance.color[0])
                        .putFloat(instance.color[1])
                        .putFloat(instance.color[2])
                        .putFloat(instance.color[3]);
            }
        }

        private InstancedVertexLayout createInstanceLayout() {
            // Create layout for instance data:
            // - vec3 position (12 bytes)
            // - float scale (4 bytes)  
            // - vec4 color (16 bytes)
            // Total: 32 bytes per instance

            // This would create the appropriate DataFormat for instance attributes
            // The exact implementation depends on your DataFormat system
            return new InstancedVertexLayout(DataFormat.builder("ww")
                    .vec3Attribute("instancePosition")  // vec3
                    .floatAttribute("instanceScale")     // float
                    .vec4Attribute("instanceColor")     // vec4
                    .build());
        }

        private RenderSetting createCubeRenderSetting() {
            // Create appropriate render setting with shaders, blending, etc.
            // This is a placeholder - implement based on your RenderSetting system
            return null; // TODO: Implement based on your render setting creation
        }
    }

    /**
     * Data for a single cube instance
     */
    public static class CubeInstance {
        public final float[] position = new float[3];  // X, Y, Z
        public final float scale;
        public final float[] color = new float[4];     // R, G, B, A

        public CubeInstance(float x, float y, float z, float scale,
                            float r, float g, float b, float a) {
            this.position[0] = x;
            this.position[1] = y;
            this.position[2] = z;
            this.scale = scale;
            this.color[0] = r;
            this.color[1] = g;
            this.color[2] = b;
            this.color[3] = a;
        }
    }

    /**
     * Example usage of the instanced rendering system
     */
    public static void demonstrateInstancedRendering() {
        // Create cube instances
        CubeInstance[] cubeInstances = {
                new CubeInstance(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f), // Red cube
                new CubeInstance(2.0f, 0.0f, 0.0f, 0.8f, 0.0f, 1.0f, 0.0f, 1.0f), // Green cube
                new CubeInstance(4.0f, 0.0f, 0.0f, 1.2f, 0.0f, 0.0f, 1.0f, 1.0f), // Blue cube
                new CubeInstance(6.0f, 0.0f, 0.0f, 0.6f, 1.0f, 1.0f, 0.0f, 1.0f), // Yellow cube
        };

        // Create model mesh for a cube (this would be loaded from your model system)
        ModelMesh cubeModel = null; // TODO: Load or create cube model

        // Create instanced graphics instance
        InstancedCube instancedCube = new InstancedCube(
                Identifier.of("example", "instanced_cubes"),
                cubeModel,
                cubeInstances
        );

        // The pipeline will automatically:
        // 1. Detect this as an instanced rendering case
        // 2. Separate it into a different batch from non-instanced instances
        // 3. Fill static vertex data (cube geometry) once
        // 4. Fill dynamic vertex data (instance attributes) for each instance
        // 5. Render using glDrawArraysInstanced or glDrawElementsInstanced

        System.out.println("Instanced cube will render " + cubeInstances.length + " instances with a single draw call");
    }

    /**
     * Example demonstrating async index-based rendering
     */
    public static void demonstrateAsyncIndexBasedRendering() {
        // Create multiple cube instances
        CubeInstance[] cubeInstances = {
                new CubeInstance(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f), // Index 0: Red cube
                new CubeInstance(2.0f, 0.0f, 0.0f, 0.8f, 0.0f, 1.0f, 0.0f, 1.0f), // Index 1: Green cube
                new CubeInstance(4.0f, 0.0f, 0.0f, 1.2f, 0.0f, 0.0f, 1.0f, 1.0f), // Index 2: Blue cube
                new CubeInstance(6.0f, 0.0f, 0.0f, 0.6f, 1.0f, 1.0f, 0.0f, 1.0f), // Index 3: Yellow cube
        };

        // Create model mesh for a cube
        ModelMesh cubeModel = null; // TODO: Load or create cube model

        // Create instanced graphics instance
        InstancedCube instancedCube = new InstancedCube(
                Identifier.of("example", "async_cubes"),
                cubeModel,
                cubeInstances
        );

        // When AsyncVertexFiller processes this batch:
        // - Each provider will be called with fillInstanceVertexData(filler, index)
        // - Provider at index 0 fills buffer position 0 with Red cube data
        // - Provider at index 1 fills buffer position 1 with Green cube data  
        // - Provider at index 2 fills buffer position 2 with Blue cube data
        // - Provider at index 3 fills buffer position 3 with Yellow cube data
        // - All filling happens asynchronously in parallel

        System.out.println("Async index-based rendering:");
        System.out.println("- Each provider knows its exact index position in the batch");
        System.out.println("- Providers fill buffer positions in parallel using CompletableFuture");
        System.out.println("- No need to pass index lists - each provider uses its batch index");
        System.out.println("- Buffer layout: [Index0_Data, Index1_Data, Index2_Data, Index3_Data]");
    }
}
