# Culling Test Shader Split Guide

## 概述

这个指南说明了如何将原始的culling测试着色器拆分为三个独立的组件，以支持新的渲染管线。

## 拆分结果

### 1. Chunk Culling 组件

**文件:**
- `culling_test_chunk.json` - 着色器程序配置
- `culling_test_chunk.vsh` - 顶点着色器
- `culling_test_chunk.fsh` - 片段着色器
- `culling_test_chunk.json` (partial_render_setting) - 渲染设置
- `ChunkCullingTestGraphics.java` - 图形实例

**特色:**
- 绿色线框 (`vec4(0.0, 1.0, 0.0, 0.35)`)
- 只使用 `sketch_testPos` uniform
- 渲染优先级: 100 (最先渲染)

### 2. Entity Culling 组件

**文件:**
- `culling_test_entity.json` - 着色器程序配置
- `culling_test_entity.vsh` - 顶点着色器
- `culling_test_entity.fsh` - 片段着色器
- `culling_test_entity.json` (partial_render_setting) - 渲染设置
- `EntityCullingTestGraphics.java` - 图形实例

**特色:**
- 红色线框 (`vec4(1.0, 0.0, 0.0, 0.35)`)
- 使用 `sketch_testEntityPos` 和 `sketch_testEntityAABB` uniforms
- 渲染优先级: 200 (中等)

### 3. Block Entity Culling 组件

**文件:**
- `culling_test_block_entity.json` - 着色器程序配置
- `culling_test_block_entity.vsh` - 顶点着色器
- `culling_test_block_entity.fsh` - 片段着色器
- `culling_test_block_entity.json` (partial_render_setting) - 渲染设置
- `BlockEntityCullingTestGraphics.java` - 图形实例

**特色:**
- 蓝色线框 (`vec4(0.0, 0.0, 1.0, 0.35)`)
- 使用 `sketch_testBlockEntityPos` 和 `sketch_testBlockEntityAABB` uniforms
- 渲染优先级: 300 (最后渲染)

## Uniform 变化

### 原始系统
```glsl
uniform vec4 sketch_testEntityPos;    // 既用于entity也用于block entity
uniform vec3 sketch_testEntityAABB;   // 既用于entity也用于block entity
```

### 新系统
```glsl
// Entity专用
uniform vec4 sketch_testEntityPos;
uniform vec3 sketch_testEntityAABB;

// Block Entity专用
uniform vec4 sketch_testBlockEntityPos;
uniform vec3 sketch_testBlockEntityAABB;

// Chunk专用 (保持不变)
uniform vec4 sketch_testPos;
```

## Java端变化

### VanillaPipelineEventHandler 

**新增的 uniform 注册:**
```java
// 分离的entity uniforms
uniformEvent.register(Identifier.of("sketch_testEntityPos"), ...);
uniformEvent.register(Identifier.of("sketch_testEntityAABB"), ...);

// 新增的block entity uniforms
uniformEvent.register(Identifier.of("sketch_testBlockEntityPos"), ...);
uniformEvent.register(Identifier.of("sketch_testBlockEntityAABB"), ...);
```

**新增的图形实例注册:**
```java
private static void registerNewPipelineCullingGraphics(RegisterStaticGraphicsEvent registerEvent) {
    // 注册三个新的图形实例
    ChunkCullingTestGraphics chunkGraphics = ...;
    EntityCullingTestGraphics entityGraphics = ...;
    BlockEntityCullingTestGraphics blockEntityGraphics = ...;
}
```

## 新管线接口

所有三个新的图形实例都实现了 `MeshGraphicsInstance` 接口：

```java
public interface MeshGraphicsInstance extends GraphicsInstance, 
        ModelMeshProvider, RenderSettingProvider {
    
    ModelMesh getModelMesh();
    <C extends RenderContext> RenderSetting getRenderSetting(C context);
    boolean needsUpdate();
    int getRenderPriority();
    boolean isVisible();
}
```

## 渲染条件

每个组件都有独立的渲染条件：

- **Chunk**: `SketchRender.testPos != null`
- **Entity**: `SketchRender.testEntity != null`
- **Block Entity**: `SketchRender.testBlockEntity != null`

## 向后兼容性

- 原始的 `CullingTestGraphics` 仍然存在并正常工作
- 新的组件以 `MeshGraphicsInstance` 形式实现，为未来的新管线做准备
- 当前它们作为legacy模式注册，与现有系统兼容

## 使用方法

1. **测试Chunk**: 使用相同的键绑定选择chunk区域
2. **测试Entity**: 使用相同的键绑定选择实体
3. **测试Block Entity**: 使用相同的键绑定选择方块实体

每种类型现在都有独特的颜色编码：
- 🟢 **Chunk**: 绿色
- 🔴 **Entity**: 红色  
- 🔵 **Block Entity**: 蓝色

## 未来迁移

当新的三阶段管线完全就绪时：
1. 实现适当的 `ModelMesh` 创建
2. 启用新管线 (`setUseNewPipeline(true)`)
3. 移除legacy兼容层
4. 利用新管线的批量渲染功能
