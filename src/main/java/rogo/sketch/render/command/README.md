# 渲染指令架构重构

## 概述

本次重构优化了渲染管道的架构，主要改进包括：

1. **统一渲染 API**: 采用 `glDrawElementsInstancedBaseVertexBaseInstance` 作为主要渲染调用
2. **精确偏移控制**: 支持 IndexOffset、BaseVertex、BaseInstance 的精确控制
3. **职责分离**: GraphicsPassGroup 负责生成渲染数据，而不是被动提取

## 主要变化

### 1. RenderCommand 重构

**新的 GL 参数支持**:
- `PrimitiveType`: 图元类型 (GL_TRIANGLES, GL_LINES 等)
- `IndexCount`: 当前 mesh 使用的索引数量
- `IndexOffset`: 当前 mesh 在 EBO 中的偏移（字节）
- `BaseVertex`: 当前 mesh 在 VBO 中的顶点基准偏移
- `InstanceCount`: 绘制实例数量
- `BaseInstance`: 实例化属性在 InstanceVBO 中的基准偏移

**新的创建方法**:
```java
// 基础工厂方法（保持兼容性）
RenderCommand.createFromFilledResource(vertexResource, instances, stageId)

// 增强方法（精确控制偏移）
RenderCommand.createWithCalculatedOffsets(
    vertexResource, instances, stageId,
    meshBaseVertex, meshIndexOffset, meshIndexCount, batchBaseInstance
)
```

### 2. VertexRenderer 更新

**统一渲染方法**:
```java
// 主要渲染方法 - 支持所有偏移类型
VertexRenderer.renderWithOffsets(
    resource, primitiveType, indexCount, indexOffset,
    baseVertex, instanceCount, baseInstance
)

// 简化方法 - 单实例渲染
VertexRenderer.renderElements(
    resource, primitiveType, indexCount, indexOffset, baseVertex
)
```

**兼容性方法**:
- `render(VertexResource)` - 渲染整个资源
- `render(VertexResource, first, count)` - 自定义偏移和数量
- `renderInstanced(...)` - 实例化渲染

### 3. GraphicsPassGroup 增强

**新的职责**:
- `collectRenderData(C context)`: 收集本阶段的渲染信息
- `createRenderCommands(C context)`: 生成本阶段的渲染指令

**架构优势**:
- RenderSetting 来源明确（来自 PassGroup，而不是实例提取）
- 减少 Pipeline 的复杂度
- 更好的封装性和可维护性

### 4. InfoCollector 简化

**变化**:
- 移除 RenderSetting 提取逻辑
- 需要外部提供 RenderSetting 参数
- 更加纯粹的信息收集功能

## 渲染流程

### 新的三阶段渲染

1. **数据收集**: GraphicsPassGroup.collectRenderData()
2. **顶点填充**: AsyncVertexFiller.fillVertexBuffersAsync()
3. **指令生成**: GraphicsPassGroup.createRenderCommands()

### GL 调用标准化

所有渲染最终通过统一的 GL 调用：
```cpp
glDrawElementsInstancedBaseVertexBaseInstance(
    primitiveType,      // GL_TRIANGLES 等
    indexCount,         // 索引数量
    GL_UNSIGNED_INT,    // 索引类型
    indexOffset,        // 索引偏移（字节）
    instanceCount,      // 实例数量
    baseVertex,         // 顶点基准偏移
    baseInstance        // 实例基准偏移
);
```

## 使用示例

### 创建 RenderCommand

```java
// 基础使用
RenderCommand command = RenderCommand.createFromFilledResource(
    vertexResource, graphicsInfos, stageId
);

// 精确控制（推荐）
RenderCommand command = RenderCommand.createWithCalculatedOffsets(
    vertexResource, graphicsInfos, stageId,
    meshStartVertex,    // mesh 在 VBO 中的起始位置
    meshIndexOffset,    // mesh 在 EBO 中的偏移
    meshIndexCount,     // mesh 的索引数量
    instanceStartIndex  // 实例在 InstanceVBO 中的起始位置
);
```

### 执行渲染

```java
// 统一渲染（推荐）
command.execute();

// 单实例渲染（调试用）
command.executeSingleDraw();
```

## 兼容性

- 保留了所有原有的 VertexRenderer 方法
- GraphicsPassGroup 的 render() 方法依然可用
- 现有的渲染代码可以渐进式迁移

## 性能优势

1. **减少 GL 调用**: 统一的渲染 API 减少状态切换
2. **更好的批处理**: 精确的偏移控制允许更有效的合批
3. **内存效率**: 共享 VBO/EBO/InstanceVBO，减少内存占用
4. **GPU 友好**: 更符合现代 GPU 的渲染模式
