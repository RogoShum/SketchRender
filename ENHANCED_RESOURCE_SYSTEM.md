# 增强的资源管理系统

## 概述

基于你现有的GraphicsResourceManager，我们在不破坏原有架构的基础上，添加了以下增强功能：

1. **Shader预处理系统** - Import/Include和宏控制
2. **自动重载机制** - RenderSetting和Shader的自动更新
3. **多资源管理支持** - 保持原有的通用资源管理特性
4. **批处理保持** - 重载后保持RenderSetting作为key的合批特性

## 核心改进

### 1. GraphicsResourceManager增强

```java
// 启用增强功能（保持向后兼容）
GraphicsResourceManager.getInstance().enableEnhancedFeatures(resourceProvider);

// 注册重载监听器（适用于所有资源类型）
manager.registerReloadListener(ResourceTypes.SHADER_PROGRAM, shaderId, 
    (name, newResource) -> {
        System.out.println("Shader " + name + " reloaded");
    });
```

**特点：**
- ✅ 保持原有的多资源管理能力
- ✅ 向后兼容，现有代码无需修改
- ✅ 可选择性启用增强功能
- ✅ 支持所有资源类型的重载监听

### 2. 可重载的RenderSetting系统

```java
// 创建可重载的PartialRenderSetting
PartialRenderSetting reloadable = PartialRenderSetting.reloadable(
    renderState, resourceBinding, shouldSwitch, sourceIdentifier
);

// 从PartialRenderSetting创建RenderSetting（自动继承重载能力）
RenderSetting setting = RenderSetting.computeShader(reloadable);

// 设置更新监听器
setting.addUpdateListener(newSetting -> {
    System.out.println("RenderSetting updated!");
});
```

**工作流程：**
```
1. ResourceManager重载 
   ↓
2. PartialRenderSetting更新
   ↓  
3. 通知所有相关的RenderSetting
   ↓
4. GraphicsPassGroup自动迁移实例
   ↓
5. 保持批处理特性，使用新的RenderSetting作为key
```

### 3. GraphicsPipeline集成

```java
// 添加可重载的实例到pipeline
pipeline.addGraphInstance(stageId, instance, reloadableSetting);

// 强制重载所有可重载的设置
pipeline.forceReloadAllRenderSettings();

// 获取重载统计信息
var stats = pipeline.getReloadableSettingsStats();
```

**特点：**
- ✅ 自动处理RenderSetting重载
- ✅ 保持实例批处理特性
- ✅ 无缝迁移到新的setting
- ✅ 统计和监控支持

## 实际使用场景

### 场景1：Shader配置热重载

```json
// assets/sketch/render/shader_program/lighting.json
{
  "vertex": "lighting/vertex",
  "fragment": "lighting/fragment",
  "config": {
    "defines": {
      "MAX_LIGHTS": 16,
      "USE_SHADOWS": true
    },
    "features": ["pbr_lighting", "shadow_mapping"]
  }
}
```

当你修改这个JSON文件时：
1. ResourceManager检测到文件变化
2. 重新加载Shader资源
3. 自动应用新的配置
4. 所有使用这个shader的RenderSetting自动更新
5. GraphicsPipeline中的实例无缝切换到新的shader

### 场景2：RenderSetting热重载

```json
// assets/sketch/render/partial_render_setting/culling.json
{
  "render_state": {
    "shader_program": "sketch:culling_compute"
  },
  "resource_binding": {
    "shader_storage_buffer": {
      "entity_data": "sketch:entity_data",
      "culling_result": "sketch:culling_result"
    }
  }
}
```

当你修改这个配置时：
1. PartialRenderSetting重新加载
2. 所有基于它的RenderSetting自动更新
3. GraphicsPassGroup迁移所有实例到新的setting
4. 保持批处理特性（相同setting的实例仍然合批）

### 场景3：Shader Import系统

```glsl
// assets/sketch/shaders/lighting/vertex.glsl
#version 430 core

#import <common/vertex_common>    // 系统导入
#import "materials/pbr_materials" // 相对导入

#ifdef PBR_LIGHTING
    #import <lighting/pbr_vertex>
#endif

layout (location = 0) in vec3 aPosition;
uniform mat4 mvpMatrix;

void main() {
    gl_Position = mvpMatrix * vec4(aPosition, 1.0);
}
```

当你修改imported文件时：
1. 预处理器检测到依赖变化
2. 自动重新编译所有依赖的shader
3. 更新所有相关的RenderSetting
4. GraphicsPipeline无缝切换到新的shader

## 系统架构

```
ResourceManager (Minecraft)
├── RenderResourceManager (你的)
│   ├── 启用增强功能
│   └── 扫描和加载资源
├── GraphicsResourceManager (增强的)
│   ├── 多资源类型管理
│   ├── 重载监听器系统
│   ├── Enhanced Shader Loader
│   └── 自动通知机制
├── PartialRenderSetting (可重载的)
│   ├── 源标识符跟踪
│   ├── 更新监听器
│   └── 自动重载机制
├── RenderSetting (可重载的)
│   ├── 继承PartialRenderSetting的重载能力
│   ├── 更新监听器
│   └── 批处理key保持
└── GraphicsPipeline/GraphicsPassGroup
    ├── 自动处理setting更新
    ├── 实例迁移
    └── 批处理特性保持
```

## 关键优势

### 1. **保持现有架构**
- ✅ 不破坏现有的GraphicsResourceManager设计
- ✅ 保持多资源类型管理能力
- ✅ 向后兼容所有现有代码

### 2. **智能重载机制**
- ✅ 自动检测资源变化
- ✅ 级联更新相关组件
- ✅ 保持渲染状态一致性

### 3. **批处理优化**
- ✅ RenderSetting重载后仍作为批处理key
- ✅ 自动迁移实例到新的setting
- ✅ 保持渲染性能

### 4. **开发体验**
- ✅ 热重载支持，无需重启
- ✅ 配置文件即时生效
- ✅ Shader导入和宏系统
- ✅ 详细的错误报告和统计

## 使用示例

```java
// 1. 在应用启动时启用增强功能
GraphicsResourceManager.getInstance().enableEnhancedFeatures(resourceManager);

// 2. 正常使用现有API（自动获得重载能力）
var partialSetting = GraphicsResourceManager.getInstance()
    .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);

RenderSetting setting = RenderSetting.computeShader(partialSetting.get());
pipeline.addGraphInstance(stageId, instance, setting);

// 3. 监控和管理
pipeline.getReloadableSettingsStats().forEach(System.out::println);
pipeline.forceReloadAllRenderSettings(); // 手动触发重载
```

这个设计完美地满足了你的需求：保持现有架构的通用性，添加自动重载功能，并且保持RenderSetting的批处理特性。
