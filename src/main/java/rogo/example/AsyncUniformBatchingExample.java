package rogo.example;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.render.GraphicsPassGroup;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.async.AsyncRenderExecutor;
import rogo.sketch.util.Identifier;

/**
 * 异步uniform合批系统示例
 * 展示tick和uniform收集的并行化优化效果
 */
public class AsyncUniformBatchingExample {

    /**
     * 模拟的重型计算实例
     */
    public static class HeavyComputeInstance implements GraphicsInstance {
        private final Identifier identifier;
        private Matrix4f transform = new Matrix4f();
        private Vector3f color = new Vector3f();
        private float roughness;
        private volatile boolean needsUpdate = true;

        public HeavyComputeInstance(String id, Vector3f color, float roughness) {
            this.identifier = Identifier.of(id);
            this.color.set(color);
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
            // 模拟重型计算（例如物理计算、AI决策等）
            simulateHeavyComputation();
            needsUpdate = false;
        }

        private void simulateHeavyComputation() {
            // 模拟复杂矩阵运算
            Matrix4f temp = new Matrix4f();
            for (int i = 0; i < 100; i++) {
                temp.rotateY(0.01f).translate(0.1f, 0, 0);
                transform.mul(temp);
            }
            
            // 模拟其他计算
            try {
                Thread.sleep(1); // 模拟1ms的计算时间
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

        // Duck typing methods for uniform collection
        public Matrix4f getModelMatrix() {
            return transform;
        }

        public Vector3f getColor() {
            return color;
        }

        public float getRoughness() {
            return roughness;
        }

        public void setColor(Vector3f newColor) {
            this.color.set(newColor);
            needsUpdate = true;
        }

        public void setRoughness(float newRoughness) {
            this.roughness = newRoughness;
            needsUpdate = true;
        }
    }

    /**
     * 创建测试实例
     */
    private static HeavyComputeInstance[] createTestInstances(int count) {
        HeavyComputeInstance[] instances = new HeavyComputeInstance[count];
        
        for (int i = 0; i < count; i++) {
            // 创建不同uniform值的实例组
            Vector3f color;
            float roughness;
            
            if (i % 3 == 0) {
                color = new Vector3f(1, 0, 0); // 红色
                roughness = 0.5f;
            } else if (i % 3 == 1) {
                color = new Vector3f(0, 1, 0); // 绿色
                roughness = 0.8f;
            } else {
                color = new Vector3f(0, 0, 1); // 蓝色
                roughness = 0.2f;
            }
            
            instances[i] = new HeavyComputeInstance("instance_" + i, color, roughness);
        }
        
        return instances;
    }

    /**
     * 测试同步vs异步性能
     */
    public static void benchmarkSyncVsAsync() {
        System.out.println("=== Sync vs Async Performance Benchmark ===");

        int[] instanceCounts = {16, 64, 256, 1024};
        
        for (int count : instanceCounts) {
            System.out.println("\n--- Testing with " + count + " instances ---");
            
            HeavyComputeInstance[] instances = createTestInstances(count);
            
            // 测试同步tick
            long syncTickTime = benchmarkSyncTick(instances);
            
            // 测试异步tick
            long asyncTickTime = benchmarkAsyncTick(instances);
            
            // 测试同步uniform收集
            long syncUniformTime = benchmarkSyncUniformCollection(instances);
            
            // 测试异步uniform收集
            long asyncUniformTime = benchmarkAsyncUniformCollection(instances);
            
            // 输出结果
            System.out.printf("Tick performance:\n");
            System.out.printf("  Sync:  %6.2f ms\n", syncTickTime / 1_000_000.0);
            System.out.printf("  Async: %6.2f ms (%.1fx faster)\n", 
                    asyncTickTime / 1_000_000.0, 
                    (double) syncTickTime / asyncTickTime);
            
            System.out.printf("Uniform collection performance:\n");
            System.out.printf("  Sync:  %6.2f ms\n", syncUniformTime / 1_000_000.0);
            System.out.printf("  Async: %6.2f ms (%.1fx faster)\n", 
                    asyncUniformTime / 1_000_000.0, 
                    (double) syncUniformTime / asyncUniformTime);
        }
    }

    private static long benchmarkSyncTick(HeavyComputeInstance[] instances) {
        MockRenderContext context = new MockRenderContext();
        
        long startTime = System.nanoTime();
        
        // 同步tick
        for (HeavyComputeInstance instance : instances) {
            if (instance.shouldTick()) {
                instance.tick(context);
            }
        }
        
        return System.nanoTime() - startTime;
    }

    private static long benchmarkAsyncTick(HeavyComputeInstance[] instances) {
        MockRenderContext context = new MockRenderContext();
        AsyncRenderExecutor asyncExecutor = AsyncRenderExecutor.getInstance();
        
        long startTime = System.nanoTime();
        
        // 异步tick
        java.util.List<GraphicsInstance> instanceList = java.util.List.of(instances);
        asyncExecutor.tickInstancesAsync(instanceList, context).join();
        
        return System.nanoTime() - startTime;
    }

    private static long benchmarkSyncUniformCollection(HeavyComputeInstance[] instances) {
        MockUniformHookGroup hookGroup = new MockUniformHookGroup();
        
        long startTime = System.nanoTime();
        
        // 同步uniform收集
        for (HeavyComputeInstance instance : instances) {
            if (instance.shouldRender()) {
                // 模拟Duck typing uniform收集
                simulateUniformCollection(hookGroup, instance);
            }
        }
        
        return System.nanoTime() - startTime;
    }

    private static long benchmarkAsyncUniformCollection(HeavyComputeInstance[] instances) {
        MockUniformHookGroup hookGroup = new MockUniformHookGroup();
        AsyncRenderExecutor asyncExecutor = AsyncRenderExecutor.getInstance();
        
        long startTime = System.nanoTime();
        
        // 异步uniform收集
        java.util.List<GraphicsInstance> instanceList = java.util.List.of(instances);
        asyncExecutor.collectUniformsAsync(instanceList, instance -> {
            return simulateUniformCollection(hookGroup, instance);
        }).join();
        
        return System.nanoTime() - startTime;
    }

    private static rogo.sketch.render.uniform.UniformValueSnapshot simulateUniformCollection(
            MockUniformHookGroup hookGroup, GraphicsInstance instance) {
        
        // 模拟Duck typing检测和uniform收集的开销
        try {
            Thread.sleep(0, 100000); // 模拟0.1ms的收集时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 创建模拟的uniform快照
        java.util.Map<String, Object> uniforms = new java.util.HashMap<>();
        if (instance instanceof HeavyComputeInstance heavyInstance) {
            uniforms.put("u_modelMatrix", heavyInstance.getModelMatrix());
            uniforms.put("u_color", heavyInstance.getColor());
            uniforms.put("u_roughness", heavyInstance.getRoughness());
        }
        
        return new rogo.sketch.render.uniform.UniformValueSnapshot(uniforms);
    }

    /**
     * 演示异步配置的影响
     */
    public static void demonstrateAsyncConfiguration() {
        System.out.println("\n=== Async Configuration Demo ===");

        // 创建GraphicsPassGroup
        var passGroup = new GraphicsPassGroup<MockRenderContext>(Identifier.of("test_stage"));
        
        // 展示默认配置
        System.out.println("Default async configuration:");
        System.out.println("  Async tick enabled: " + passGroup.isAsyncTickEnabled());
        System.out.println("  Async uniform collection enabled: " + passGroup.isAsyncUniformCollectionEnabled());
        System.out.println("  Async threshold: " + passGroup.getAsyncThreshold());
        
        // 调整配置
        passGroup.setAsyncThreshold(16); // 降低异步阈值
        passGroup.setAsyncTickEnabled(true);
        passGroup.setAsyncUniformCollectionEnabled(true);
        
        System.out.println("\nUpdated configuration:");
        System.out.println("  Async threshold: " + passGroup.getAsyncThreshold());
        
        // 模拟获取性能统计
        AsyncRenderExecutor.AsyncPerformanceStats stats = passGroup.getAsyncPerformanceStats();
        System.out.println("\nCurrent performance stats:");
        System.out.println("  " + stats);
        
        // 展示阶段统计
        var stageStats = passGroup.getStageStats();
        System.out.println("\nStage statistics:");
        System.out.println("  " + stageStats);
    }

    /**
     * 模拟渲染上下文
     */
    private static class MockRenderContext extends RenderContext {
        // 简化的模拟实现
    }

    /**
     * 模拟UniformHookGroup
     */
    private static class MockUniformHookGroup {
        // 简化的模拟实现
    }

    /**
     * 展示最佳实践建议
     */
    public static void showBestPractices() {
        System.out.println("\n=== Async Optimization Best Practices ===");
        
        System.out.println("1. 异步阈值设置:");
        System.out.println("   - 小于32个实例: 同步处理 (避免线程开销)");
        System.out.println("   - 32-128个实例: 考虑启用异步");
        System.out.println("   - 超过128个实例: 强烈建议异步");
        
        System.out.println("\n2. 线程池配置:");
        System.out.println("   - Tick线程池: CPU核心数/2 (CPU密集型)");
        System.out.println("   - Uniform线程池: CPU核心数/4 (轻量级任务)");
        System.out.println("   - 使用守护线程避免阻止JVM退出");
        
        System.out.println("\n3. 性能监控:");
        System.out.println("   - 监控异步执行时间vs同步时间");
        System.out.println("   - 观察线程池使用情况");
        System.out.println("   - 根据实际负载调整阈值");
        
        System.out.println("\n4. 错误处理:");
        System.out.println("   - 异步任务异常不会中断渲染");
        System.out.println("   - 记录错误日志用于调试");
        System.out.println("   - 提供降级到同步模式的机制");
    }

    public static void main(String[] args) {
        benchmarkSyncVsAsync();
        demonstrateAsyncConfiguration();
        showBestPractices();
        
        // 关闭异步执行器（在实际应用中通常在程序退出时调用）
        AsyncRenderExecutor.getInstance().shutdown();
    }
}
