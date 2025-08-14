package rogo.example;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.shader.uniform.UniformValueSnapshot;
import rogo.sketch.util.Identifier;

/**
 * 示例：基于Duck typing的动态uniform合批系统
 * 运行时自动收集uniform值并进行合批，无需预定义签名
 */
public class UniformBatchingExample {

    /**
     * 示例实例类：通过Duck typing自动提供uniform
     */
    public static class ModelInstance implements GraphicsInstance {
        private final Identifier identifier;
        private final Matrix4f modelMatrix;
        private final Vector3f color;
        private final float roughness;

        public ModelInstance(String id, Matrix4f modelMatrix, Vector3f color, float roughness) {
            this.identifier = Identifier.of(id);
            this.modelMatrix = new Matrix4f(modelMatrix);
            this.color = new Vector3f(color);
            this.roughness = roughness;
        }

        @Override
        public Identifier getIdentifier() {
            return identifier;
        }

        @Override
        public boolean shouldTick() {
            return true;
        }

        @Override
        public <C extends RenderContext> void tick(C context) {
            // 实例更新逻辑
        }

        @Override
        public boolean shouldDiscard() {
            return false;
        }

        @Override
        public boolean shouldRender() {
            return true;
        }

        @Override
        public void endDraw() {
            // 绘制完成
        }

        // Duck typing: UniformHook会自动检测这些方法
        public Matrix4f getModelMatrix() {
            return modelMatrix;
        }

        public Vector3f getColor() {
            return color;
        }

        public float getRoughness() {
            return roughness;
        }

        // 便于演示
        public void setColor(Vector3f newColor) {
            this.color.set(newColor);
        }
    }

    /**
     * 模拟UniformHook的Duck typing检测机制
     */
    public static class MockUniformHook {
        private final String uniformName;
        private final String methodName;

        public MockUniformHook(String uniformName, String methodName) {
            this.uniformName = uniformName;
            this.methodName = methodName;
        }

        public Object getValue(Object instance) {
            try {
                java.lang.reflect.Method method = instance.getClass().getMethod(methodName);
                return method.invoke(instance);
            } catch (Exception e) {
                return null; // uniform不适用于此实例
            }
        }

        public String getUniformName() {
            return uniformName;
        }
    }

    /**
     * 模拟UniformHookGroup的Duck typing收集
     */
    public static class MockUniformHookGroup {
        private final java.util.List<MockUniformHook> hooks = java.util.List.of(
                new MockUniformHook("u_modelMatrix", "getModelMatrix"),
                new MockUniformHook("u_color", "getColor"), 
                new MockUniformHook("u_roughness", "getRoughness"),
                new MockUniformHook("u_time", "getTime"), // 这个方法不存在，会被忽略
                new MockUniformHook("u_metallic", "getMetallic") // 这个也不存在
        );

        /**
         * 模拟收集uniform快照的过程
         */
        public UniformValueSnapshot captureSnapshot(Object instance) {
            java.util.Map<String, Object> values = new java.util.HashMap<>();

            for (MockUniformHook hook : hooks) {
                Object value = hook.getValue(instance);
                if (value != null) {
                    values.put(hook.getUniformName(), value);
                }
            }

            return new UniformValueSnapshot(values);
        }
    }

    /**
     * 演示Duck typing自动uniform收集和合批
     */
    public static void demonstrateDuckTypingBatching() {
        System.out.println("=== Duck Typing Uniform Batching Example ===");

        // 创建多个实例
        ModelInstance[] instances = {
                // 组1: 红色，粗糙度0.5
                new ModelInstance("cube1", new Matrix4f().translate(0, 0, 0), new Vector3f(1, 0, 0), 0.5f),
                new ModelInstance("cube2", new Matrix4f().translate(1, 0, 0), new Vector3f(1, 0, 0), 0.5f),
                new ModelInstance("cube3", new Matrix4f().translate(2, 0, 0), new Vector3f(1, 0, 0), 0.5f),

                // 组2: 绿色，粗糙度0.5  
                new ModelInstance("sphere1", new Matrix4f().translate(0, 1, 0), new Vector3f(0, 1, 0), 0.5f),
                new ModelInstance("sphere2", new Matrix4f().translate(1, 1, 0), new Vector3f(0, 1, 0), 0.5f),

                // 组3: 蓝色，粗糙度0.8
                new ModelInstance("plane1", new Matrix4f().translate(0, 0, 1), new Vector3f(0, 0, 1), 0.8f),
        };

        MockUniformHookGroup hookGroup = new MockUniformHookGroup();

        System.out.println("\n--- Duck Typing Uniform Collection ---");
        
        // 使用Duck typing收集每个实例的uniform
        java.util.Map<UniformValueSnapshot, java.util.List<ModelInstance>> batches = new java.util.HashMap<>();
        
        for (ModelInstance instance : instances) {
            // Duck typing自动收集uniform值
            UniformValueSnapshot snapshot = hookGroup.captureSnapshot(instance);
            
            System.out.println("Instance " + instance.getIdentifier() + ":");
            System.out.println("  Duck typing detected uniforms: " + snapshot);
            
            batches.computeIfAbsent(snapshot, k -> new java.util.ArrayList<>()).add(instance);
        }

        // 分析合批结果
        System.out.println("\n--- Batching Results ---");
        System.out.println("Total instances: " + instances.length);
        System.out.println("Number of batches: " + batches.size());

        int batchIndex = 1;
        for (java.util.Map.Entry<UniformValueSnapshot, java.util.List<ModelInstance>> entry : batches.entrySet()) {
            UniformValueSnapshot snapshot = entry.getKey();
            java.util.List<ModelInstance> batchInstances = entry.getValue();

            System.out.println("\nBatch " + batchIndex + " (" + batchInstances.size() + " instances):");
            System.out.println("  Uniform values: " + snapshot);
            System.out.print("  Instances: ");
            for (ModelInstance instance : batchInstances) {
                System.out.print(instance.getIdentifier() + " ");
            }
            System.out.println();
            batchIndex++;
        }
    }

    /**
     * 演示运行时uniform变化的处理
     */
    public static void demonstrateDynamicUniformChange() {
        System.out.println("\n=== Dynamic Uniform Change ===");

        ModelInstance instance1 = new ModelInstance("test1", new Matrix4f(), new Vector3f(1, 0, 0), 0.5f);
        ModelInstance instance2 = new ModelInstance("test2", new Matrix4f(), new Vector3f(1, 0, 0), 0.5f);
        
        MockUniformHookGroup hookGroup = new MockUniformHookGroup();

        // 初始状态
        UniformValueSnapshot snapshot1 = hookGroup.captureSnapshot(instance1);
        UniformValueSnapshot snapshot2 = hookGroup.captureSnapshot(instance2);
        
        System.out.println("Initial state:");
        System.out.println("  Instance1 uniforms: " + snapshot1);
        System.out.println("  Instance2 uniforms: " + snapshot2);
        System.out.println("  Can batch together: " + snapshot1.isCompatibleWith(snapshot2));

        // 改变instance1的颜色
        instance1.setColor(new Vector3f(0, 1, 0));
        
        UniformValueSnapshot newSnapshot1 = hookGroup.captureSnapshot(instance1);
        
        System.out.println("\nAfter changing instance1 color:");
        System.out.println("  Instance1 uniforms: " + newSnapshot1);
        System.out.println("  Instance2 uniforms: " + snapshot2);
        System.out.println("  Can batch together: " + newSnapshot1.isCompatibleWith(snapshot2));
        
        System.out.println("\n✅ Duck typing automatically detects uniform changes!");
        System.out.println("✅ No need to manually define uniform signatures!");
    }

    /**
     * 演示渲染流程中的应用
     */
    public static void demonstrateRenderPipelineUsage() {
        System.out.println("\n=== Render Pipeline Usage ===");

        System.out.println("Render flow:");
        System.out.println("1. applyRenderSetting() - 设置全局uniform (相机、矩阵等)");
        System.out.println("2. collectUniformBatches() - Duck typing收集实例uniform");
        System.out.println("3. 按uniform值分组实例");
        System.out.println("4. 每组执行一次draw call:");
        System.out.println("   - 应用组内uniform值");
        System.out.println("   - 填充顶点数据");  
        System.out.println("   - 执行渲染");

        // 模拟实际流程
        ModelInstance[] instances = {
                new ModelInstance("a", new Matrix4f(), new Vector3f(1, 0, 0), 0.5f), // 红色
                new ModelInstance("b", new Matrix4f(), new Vector3f(1, 0, 0), 0.5f), // 红色  
                new ModelInstance("c", new Matrix4f(), new Vector3f(0, 1, 0), 0.8f), // 绿色
        };

        MockUniformHookGroup hookGroup = new MockUniformHookGroup();
        
        System.out.println("\nSimulated render process:");
        System.out.println("-> applyRenderSetting: Global uniforms set");
        
        // 收集和分组
        java.util.Map<UniformValueSnapshot, java.util.List<ModelInstance>> batches = new java.util.HashMap<>();
        for (ModelInstance instance : instances) {
            UniformValueSnapshot snapshot = hookGroup.captureSnapshot(instance);
            batches.computeIfAbsent(snapshot, k -> new java.util.ArrayList<>()).add(instance);
        }
        
        // 渲染每个批次
        for (java.util.Map.Entry<UniformValueSnapshot, java.util.List<ModelInstance>> entry : batches.entrySet()) {
            UniformValueSnapshot snapshot = entry.getKey();
            java.util.List<ModelInstance> batchInstances = entry.getValue();
            
            System.out.println("-> Render batch (" + batchInstances.size() + " instances):");
            System.out.println("   - Apply instance uniforms: " + snapshot);
            System.out.println("   - Fill vertex data for instances: " + 
                batchInstances.stream().map(i -> i.getIdentifier().toString()).toList());
            System.out.println("   - Execute draw call");
        }
        
        System.out.println("\n✅ Efficient batching with minimal draw calls!");
        System.out.println("✅ Duck typing makes it flexible and automatic!");
    }

    public static void main(String[] args) {
        demonstrateDuckTypingBatching();
        demonstrateDynamicUniformChange();
        demonstrateRenderPipelineUsage();
    }
}
