# SketchRender Advanced Features

本文档介绍 SketchRender 的高级功能，包括实例池管理和异步渲染控制，以及它们与 GraphicsPipeline 的集成。

## 1. 实例池管理系统

### 核心组件

- **`ObjectPool<T>`**: 通用对象池实现，支持线程安全的对象借用和归还
- **`InstancePoolManager`**: 管理不同类型的 GraphicsInstance 池
- **`PoolableGraphicsInstance`**: 支持池化的实例接口
- **`Poolable`**: 基础池化接口，提供重置功能

### 使用方式

```java
// 1. 注册池
InstancePoolManager poolManager = InstancePoolManager.getInstance();
poolManager.registerTypePool(MyInstance.class, MyInstance::new, 100);
poolManager.registerNamedPool(Identifier.of("particles"), ParticleInstance::new, 200);

// 2. 借用实例
MyInstance instance = poolManager.borrowInstance(MyInstance.class);
GraphicsInstance particle = poolManager.borrowInstance(Identifier.of("particles"));

// 3. 配置实例（如果是 PoolableGraphicsInstance）
if (particle instanceof PoolableGraphicsInstance poolable) {
    poolable.configure("param1", "param2");
}

// 4. 归还实例
poolManager.returnInstance(instance);
poolManager.returnInstance(particle);
```

### 优势

- **内存优化**: 减少对象创建和垃圾回收开销
- **性能提升**: 预分配对象池，避免运行时分配延迟
- **灵活配置**: 支持类型和命名两种池管理方式
- **线程安全**: 并发环境下的安全操作

## 2. 异步渲染管理系统

### 核心组件

- **`AsyncRenderManager`**: 中央异步渲染管理器
- **`AsyncRenderConfig`**: 异步渲染配置
- **`RenderExecutionMode`**: 执行模式枚举

### 执行模式

- **SYNC**: 同步执行所有操作
- **ASYNC**: 异步执行所有操作
- **ADAPTIVE**: 根据工作负载自适应选择
- **HYBRID**: 混合模式，不同操作使用不同策略

### 配置示例

```java
AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
AsyncRenderConfig config = asyncManager.getConfig();

// 配置全局模式
config.setGlobalMode(RenderExecutionMode.ADAPTIVE);

// 配置各项异步功能
config.setAsyncTickEnabled(true);
config.setAsyncVertexFillEnabled(true);
config.setAsyncUniformCollectionEnabled(true);

// 配置阈值
config.setAsyncThreshold(32);
config.setVertexFillThreshold(16);
config.setUniformCollectionThreshold(24);

// 配置线程池
config.setMaxThreads(8);
config.setCoreThreads(4);

// 应用配置
asyncManager.updateConfig(config);
```

### 使用方式

```java
// 异步实例更新
Collection<GraphicsInstance> instances = getInstances();
CompletableFuture<Void> tickFuture = asyncManager.tickInstancesAsync(instances, context);

// 异步顶点填充
CompletableFuture<VertexResult> vertexFuture = asyncManager.fillVertexAsync(() -> {
    // 顶点填充逻辑
    return fillVertexData();
}, instances.size());

// 异步 Uniform 收集
CompletableFuture<UniformResult> uniformFuture = asyncManager.collectUniformsAsync(() -> {
    // Uniform 收集逻辑
    return collectUniforms();
}, instances.size());

// 等待所有异步操作完成
CompletableFuture.allOf(tickFuture, vertexFuture, uniformFuture).join();
```

## 3. GraphicsPipeline 集成

### 核心改进

- **自动初始化**: 通过 `initialize()` 方法触发事件驱动的初始化
- **实例池集成**: 直接支持从池中借用和添加实例
- **异步支持**: 集成 AsyncRenderManager 进行智能异步处理
- **自动清理**: 定期清理废弃实例并返回到池中

### 使用方式

```java
// 创建并初始化管线
McGraphicsPipeline pipeline = new McGraphicsPipeline(true);
pipeline.initialize(); // 触发 GraphicsPipelineInitEvent

// 从实例池添加实例
pipeline.addPooledGraphInstance(
    Identifier.of("vanilla_entities"),
    MyGraphicsInstance.class,
    renderSetting
);

// 从命名池添加实例
pipeline.addNamedPoolGraphInstance(
    Identifier.of("vanilla_particle"),
    Identifier.of("particles"),
    renderSetting
);

// 执行异步tick
pipeline.tickAllStages();

// 清理废弃实例
pipeline.cleanupInstances();
```

## 4. 事件系统集成

### EventBusBridge 兼容

使用现有的 EventBusBridge 系统来适配 Forge 和 Fabric：

```java
// 注册事件处理器
EventBusBridge.subscribe(
    GraphicsPipelineInitEvent.class,
    this::onPipelineInit
);

// 发布事件
EventBusBridge.post(new GraphicsPipelineInitEvent(pipeline, InitPhase.EARLY));
```

### Vanilla 事件处理

```java
// 自动注册 Vanilla 事件处理器
VanillaPipelineEventHandler.register();

// 事件会自动处理 MC 渲染阶段的注册
```

## 5. 集成工作流

### 完整示例

```java
public class IntegratedRenderingSetup {
    
    public void initialize() {
        // 1. 配置实例池
        InstancePoolManager poolManager = InstancePoolManager.getInstance();
        poolManager.registerTypePool(ParticleInstance.class, ParticleInstance::new, 200);
        poolManager.registerNamedPool(Identifier.of("effects"), EffectInstance::new, 100);
        
        // 2. 配置异步渲染
        AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
        AsyncRenderConfig config = asyncManager.getConfig();
        config.setGlobalMode(RenderExecutionMode.ADAPTIVE);
        config.setAsyncThreshold(16);
        asyncManager.updateConfig(config);
        
        // 3. 注册事件监听器
        EventBusBridge.subscribe(GraphicsPipelineInitEvent.class, this::onPipelineInit);
        VanillaPipelineEventHandler.register();
        
        // 4. 创建并初始化渲染管线
        McGraphicsPipeline pipeline = new McGraphicsPipeline(true);
        pipeline.initialize(); // 这会自动触发事件
    }
    
    private void onPipelineInit(GraphicsPipelineInitEvent<?> event) {
        // 注册自定义阶段和实例
    }
}
```

## 6. 性能优化建议

### 实例池配置

- 根据使用模式调整池大小
- 监控池统计信息，优化池配置
- 实现适当的 `reset()` 方法避免状态泄漏

### 异步渲染配置

- 根据硬件配置调整线程数
- 监控性能统计，调整阈值
- 在低性能设备上考虑使用同步模式

### 事件系统优化

- 避免在事件处理器中执行耗时操作
- 使用适当的事件优先级
- 考虑取消不必要的事件以提高性能

## 7. 调试和监控

### 启用调试模式

```bash
# 启用事件系统调试
-Dsketchrender.event.debug=true
```

### 性能监控

```java
// 获取各系统统计信息
InstancePoolManager.Stats poolStats = poolManager.getStats();
AsyncRenderManager.AsyncPerformanceStats asyncStats = asyncManager.getPerformanceStats();
EventBus.EventBusStats eventStats = eventBus.getStats();

System.out.println("Pool: " + poolStats);
System.out.println("Async: " + asyncStats);
System.out.println("Events: " + eventStats);
```

这些高级功能为 SketchRender 提供了企业级的渲染管理能力，支持高性能、可扩展的渲染架构。
