# Shader预处理器Bug修复

## 问题描述

在新的shader预处理系统中，源码中的`#define`指令被错误地注释掉了，导致编译错误。

**问题现象：**
```glsl
// 原始代码
#define MIP_LEVELS 4
#define TILE_SIZE_X 16
#define TILE_SIZE_Y 16

// 被预处理器错误处理成：
// #define MIP_LEVELS 4
// #define TILE_SIZE_X 16
// #define TILE_SIZE_Y 16
```

## 根本原因

预处理器在处理`#define`指令时，错误地将它们注释掉了：

```java
// 错误的逻辑 (已修复)
if (processDefine(line, localMacros)) {
    if (shouldIncludeLine) {
        result.add("// " + line); // ❌ 错误：注释掉了#define
    }
    continue;
}
```

## 修复方案

修改预处理器逻辑，区分处理和保留：

```java
// 正确的逻辑 (修复后)
if (processDefine(line, localMacros)) {
    if (shouldIncludeLine) {
        result.add(line); // ✅ 正确：保留原始#define
    }
    continue;
}
```

## 设计原理

Shader预处理器应该支持两种类型的宏：

### 1. 配置宏 (Configuration Macros)
- 来源：JSON配置文件、ShaderConfiguration
- 用途：条件编译控制、特性开关
- 处理：在预处理阶段进行替换和条件判断

```json
{
  "config": {
    "defines": {
      "MAX_LIGHTS": 16,
      "ENABLE_SHADOWS": true
    }
  }
}
```

### 2. 源码宏 (Source Macros)
- 来源：Shader源码中的`#define`
- 用途：常量定义、代码复用
- 处理：**保留原样**，交给OpenGL编译器处理

```glsl
#define MIP_LEVELS 4
#define TILE_SIZE_X 16
#define COMPUTE_SHADER_WORK_GROUP_SIZE 32
```

## 预处理流程

```
1. 解析源码中的#define → 添加到本地宏表 (用于条件编译)
2. 保留原始#define行 → 输出到最终源码 (供OpenGL编译器使用)  
3. 处理条件编译指令 (#ifdef, #ifndef, #if等)
4. 应用宏替换 (配置宏 + 源码宏)
5. 处理import指令
```

## 验证修复

修复后，以下代码应该正确工作：

```glsl
#version 430 core

// 这些#define会被保留
#define MIP_LEVELS 4
#define TILE_SIZE_X 16
#define TILE_SIZE_Y 16
#define TILE_EDGE_X TILE_SIZE_X * 2
#define TILE_EDGE_Y TILE_SIZE_Y * 2

layout (local_size_x = TILE_SIZE_X, local_size_y = TILE_SIZE_Y) in;

void main() {
    int levels = MIP_LEVELS;
    int edgeX = TILE_EDGE_X;
    int edgeY = TILE_EDGE_Y;
}
```

## 影响范围

这个修复影响所有使用新预处理系统的shader：

- ✅ 源码中的`#define`现在会被正确保留
- ✅ 配置宏仍然正常工作
- ✅ 条件编译功能不受影响
- ✅ Import功能不受影响

## 向后兼容性

- ✅ 完全向后兼容
- ✅ 不影响现有的shader配置
- ✅ 不需要修改任何用户代码
