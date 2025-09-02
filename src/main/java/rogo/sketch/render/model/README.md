# Mesh和ModelMesh系统

这个包实现了一个完整的网格数据管理系统，包含逻辑网格（Mesh）和GPU就绪的模型网格（ModelMesh）。

## 核心组件

### 1. MeshBone（网格骨骼）
- 表示骨骼层次结构中的一个骨骼
- 支持父子关系和关节变换
- 包含局部变换和逆绑定姿势矩阵
- 提供全局变换的自动计算和缓存

**主要特性：**
- `parent/children`: 骨骼层次结构
- `localTransform`: 相对于父骨骼的变换
- `inverseBindPose`: 逆绑定姿势矩阵
- `jointRadius`: 关节半径（用于选择和剔除）

### 2. SubMesh（子网格）
- 表示网格中的一个子部分
- 绑定到特定骨骼
- 包含顶点数据和索引数据
- 支持不同的原始类型和顶点格式

**主要特性：**
- `bone`: 绑定的骨骼
- `vertexCount/indexCount`: 顶点和索引数量
- `primitiveType`: 原始类型（三角形、四边形等）
- `vertexFormat`: 顶点数据格式
- `vertices/indices`: 原始顶点和索引数据

### 3. Mesh（网格）
- 逻辑网格表示，存储网格、骨骼和顶点信息
- 管理骨骼层次结构和子网格列表
- 用于数据存储和编辑，不直接用于渲染

**主要特性：**
- `rootBone`: 根骨骼
- `subMeshes`: 子网格列表
- `metadata`: 元数据存储
- 骨骼和子网格的查找和管理功能

### 4. SubMeshInstance（子网格实例）
- 编译后的子网格，包含GPU缓冲区偏移信息
- 用于批量渲染时的绘制指令
- 记录在合并缓冲区中的位置

**主要特性：**
- `vertexOffset/indexOffset`: 在GPU缓冲区中的偏移
- `vertexCount/indexCount`: 顶点和索引数量
- 渲染属性（可见性、优先级、材质）

### 5. ModelMesh（模型网格）
- GPU就绪的网格，数据已填充到VertexResource
- 支持高效的批量渲染
- 每个子网格记录其在合并缓冲区中的偏移信息

**主要特性：**
- `vertexResource`: GPU顶点资源
- `subMeshInstances`: 子网格实例列表
- `bones`: 骨骼数据（用于动画）
- 批量渲染支持

### 6. MeshCompiler（网格编译器）
- 将Mesh编译为ModelMesh的工具类
- 合并顶点数据到单一GPU缓冲区
- 计算子网格偏移信息
- 支持多种编译选项

**编译选项：**
- `vertexUsage`: 顶点缓冲区使用模式
- `validateData`: 数据验证
- `optimizeVertexOrder`: 顶点顺序优化
- `mergeCompatibleSubMeshes`: 合并兼容的子网格

### 7. MeshBuilder（网格构建器）
- 流式API用于程序化创建网格
- 支持骨骼层次结构构建
- 便捷的子网格创建和绑定
- 包含常用网格模板

## 使用流程

### 1. 创建简单网格
```java
// 定义顶点格式
DataFormat format = DataFormat.builder("PositionUV")
    .vec3Attribute("position")
    .vec2Attribute("texCoord")
    .build();

// 创建网格
Mesh mesh = MeshBuilder.create("quad")
    .subMesh("main", 0, 4, PrimitiveType.QUADS, format)
    .vertices(
        -1.0f, -1.0f, 0.0f,  0.0f, 0.0f,
         1.0f, -1.0f, 0.0f,  1.0f, 0.0f,
         1.0f,  1.0f, 0.0f,  1.0f, 1.0f,
        -1.0f,  1.0f, 0.0f,  0.0f, 1.0f
    )
    .indices(0, 1, 2, 3)
    .build();
```

### 2. 创建带骨骼的网格
```java
Mesh skeletalMesh = MeshBuilder.create("character")
    .rootBone("root", 0)
    .childBone("spine", 1)
    .childBone("head", 2)
    .subMesh("head_mesh", 0, 3, PrimitiveType.TRIANGLES, format)
    .bindToBone()
    .vertices(/* vertex data */)
    .build();
```

### 3. 编译为ModelMesh
```java
// 静态网格编译
ModelMesh modelMesh = MeshCompiler.compile(mesh, 
    MeshCompiler.staticMeshOptions());

// 动态网格编译（用于动画）
ModelMesh animatedMesh = MeshCompiler.compile(skeletalMesh,
    MeshCompiler.dynamicMeshOptions());
```

### 4. 渲染
```java
// 绑定资源
modelMesh.bind();

// 按优先级渲染可见子网格
for (SubMeshInstance instance : modelMesh.getVisibleSubMeshes()) {
    // 发出绘制指令
    // glDrawElements() 或 glDrawArrays()
    // 使用 instance.getVertexOffset() 和 instance.getIndexOffset()
}

modelMesh.unbind();
```

### 5. 动画
```java
// 更新骨骼变换
MeshBone bone = modelMesh.findBone("arm");
bone.setLocalTransform(newTransform);

// 更新所有骨骼变换
modelMesh.updateBoneTransforms();

// 获取骨骼矩阵用于着色器
for (MeshBone bone : modelMesh.getBones()) {
    Matrix4f boneMatrix = bone.getBoneMatrix();
    // 上传到着色器uniform
}
```

## 批量渲染

系统支持按原始类型分组进行批量渲染：

```java
var subMeshesByType = modelMesh.getSubMeshesByPrimitiveType();

for (var entry : subMeshesByType.entrySet()) {
    PrimitiveType type = entry.getKey();
    List<SubMeshInstance> instances = entry.getValue();
    
    // 为这种原始类型设置渲染状态
    setupRenderState(type);
    
    // 批量绘制所有实例
    for (SubMeshInstance instance : instances) {
        drawInstance(instance);
    }
}
```

## 设计理念

1. **分离关注点**: Mesh用于逻辑表示，ModelMesh用于GPU渲染
2. **批量效率**: 合并顶点数据到单一缓冲区，支持批量绘制
3. **灵活性**: 支持各种顶点格式和原始类型
4. **动画支持**: 完整的骨骼层次结构和变换系统
5. **资源管理**: 自动资源清理和生命周期管理

这个系统提供了从网格创建到GPU渲染的完整工作流，同时保持了高性能和易用性。
