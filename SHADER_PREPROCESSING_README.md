# 现代化Shader预处理系统

这是一个为SketchLib设计的现代化shader预处理系统，提供了类似Minecraft原版和Iris的import功能，以及更加灵活的宏控制和重新编译机制。

## 主要特性

### 1. Shader Import/Include系统
- 支持相对路径和绝对路径导入
- 循环导入检测
- 依赖关系跟踪
- 支持两种导入语法：
  - `#import "relative/path"` - 相对导入，导入失败会报错
  - `#import <system/path>` - 系统导入，导入失败只会警告

### 2. 宏定义和条件编译
- 完整的预处理器指令支持：`#define`, `#undef`, `#ifdef`, `#ifndef`, `#if`, `#else`, `#endif`
- 运行时宏定义和配置
- 表达式求值（基本支持）
- 宏替换

### 3. 动态重新编译
- 配置变更时自动重新编译
- 依赖文件变更时重新编译相关shader
- 手动强制重新编译
- 编译错误恢复

### 4. 配置管理系统
- 全局和per-shader配置
- 预设配置（debug, performance, quality, compatibility）
- 配置变更监听
- JSON配置文件支持

## 核心组件

### ShaderPreprocessor接口
```java
public interface ShaderPreprocessor {
    PreprocessorResult process(String source, Identifier shaderIdentifier, Map<String, String> macros);
    void setResourceProvider(ShaderResourceProvider resourceProvider);
    Set<Identifier> getLastImportedFiles();
}
```

### RecompilableShader类
继承自原有的Shader类，添加了重新编译功能：
```java
public abstract class RecompilableShader extends Shader {
    public void recompile() throws IOException;
    public boolean needsRecompilation();
    public Set<Identifier> getDependencies();
}
```

### ShaderConfiguration类
管理shader编译配置：
```java
ShaderConfiguration config = ShaderConfiguration.builder()
    .define("MAX_LIGHTS", 8)
    .enableFeature("shadows")
    .setProperty("quality", "high")
    .build();
```

### AdvancedShaderManager
统一管理所有shader：
```java
AdvancedShaderManager manager = AdvancedShaderManager.getInstance();
RecompilableGraphicsShader shader = manager.createGraphicsShader(id, vertexSource, fragmentSource);
```

## 使用示例

### 1. 基本使用
```java
// 初始化
AdvancedShaderManager.initialize(resourceProvider);
AdvancedShaderManager manager = AdvancedShaderManager.getInstance();

// 创建配置
manager.setShaderConfiguration(shaderId, ShaderConfiguration.builder()
    .define("MAX_LIGHTS", 16)
    .enableFeature("pbr_lighting")
    .build());

// 创建shader
RecompilableGraphicsShader shader = manager.createGraphicsShader(
    shaderId, vertexSource, fragmentSource
);
```

### 2. Shader源码示例
```glsl
#version 430 core

#import <common/vertex_common>
#import "lighting/advanced_lighting"

#ifdef USE_SHADOWS
    #define SHADOW_SAMPLES 4
#else
    #define SHADOW_SAMPLES 1
#endif

layout (location = 0) in vec3 aPosition;
uniform mat4 mvpMatrix;

#ifdef DEBUG
    out vec3 debugColor;
#endif

void main() {
    gl_Position = mvpMatrix * vec4(aPosition, 1.0);
    
    #ifdef DEBUG
    debugColor = vec3(1.0, 0.0, 0.0);
    #endif
}
```

### 3. JSON配置文件
```json
{
  "vertex": "shaders/advanced_vertex",
  "fragment": "shaders/advanced_fragment",
  "config": {
    "preset": "quality",
    "defines": {
      "MAX_LIGHTS": 32,
      "USE_SHADOWS": true,
      "SHADOW_MAP_SIZE": 2048
    },
    "features": ["pbr_lighting", "ambient_occlusion"],
    "properties": {
      "quality_level": "ultra"
    }
  }
}
```

### 4. 动态配置变更
```java
// 运行时修改配置
manager.updateShaderConfiguration(shaderId, config -> {
    config.define("MAX_LIGHTS", 32);
    config.enableFeature("volumetric_fog");
});

// 应用预设
manager.applyPreset(shaderId, "debug");

// 检查并重新编译
manager.recompileIfNeeded();
```

## 高级功能

### 1. 依赖跟踪
系统会自动跟踪shader的依赖关系，当imported文件发生变化时，可以重新编译所有相关的shader：

```java
// 获取依赖某个文件的所有shader
Set<Identifier> dependentShaders = manager.getShadersUsingFile(fileId);

// 重新编译依赖的shader
manager.recompileDependentShaders(changedFileId);
```

### 2. 性能监控
```java
AdvancedShaderManager.ShaderManagerStats stats = manager.getStats();
System.out.println("Total shaders: " + stats.totalShaders());
System.out.println("Need recompilation: " + stats.shadersNeedingRecompilation());
```

### 3. 配置监听
```java
ShaderConfigurationManager.getInstance().addConfigurationListener(shaderId, newConfig -> {
    System.out.println("Configuration changed: " + newConfig);
});
```

## 与现有系统集成

### 1. 更新ResourceLoader
使用`EnhancedShaderProgramLoader`替代原有的`ShaderProgramLoader`：

```java
// 在ResourceManager中注册
GraphicsResourceManager.getInstance().registerLoader(
    ResourceTypes.SHADER_PROGRAM, 
    new EnhancedShaderProgramLoader(resourceProvider)
);
```

### 2. 兼容性
- 新系统完全向后兼容现有的Shader类
- 可以选择性地启用预处理功能
- 现有的shader代码无需修改即可使用

## 预设配置

系统提供了几个内置预设：

- **debug**: 启用调试功能，添加验证
- **performance**: 优化性能，禁用昂贵的效果
- **quality**: 高质量渲染，启用所有特性
- **compatibility**: 兼容模式，使用较低的GLSL版本

## 最佳实践

1. **组织Import文件**: 将通用功能放在`<>`导入的系统文件中，项目特定的功能使用`""`导入
2. **合理使用宏**: 避免过度使用宏，保持代码可读性
3. **配置管理**: 使用配置文件管理复杂的shader设置
4. **依赖最小化**: 尽量减少不必要的依赖，提高编译性能
5. **错误处理**: 始终处理编译错误，提供降级方案

## 故障排除

### 常见问题

1. **循环导入**: 检查import路径，确保没有循环依赖
2. **文件找不到**: 验证import路径和资源提供者配置
3. **编译错误**: 检查宏定义和条件编译逻辑
4. **性能问题**: 监控重新编译频率，优化配置变更

### 调试技巧

1. 启用debug预设查看详细信息
2. 使用`getStats()`监控系统状态
3. 检查依赖关系图排查问题
4. 使用配置监听器跟踪变更

这个系统提供了现代化、灵活且强大的shader管理能力，可以大大提高shader开发效率和运行时性能。
