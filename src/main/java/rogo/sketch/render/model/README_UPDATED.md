# 更新的Mesh和ModelMesh系统

经过重构，Mesh系统现在已经完全集成到资源管理系统中，并支持Minecraft模型转换。

## 主要变更

### 1. PrimitiveType由Mesh统一决定
- 所有SubMesh现在跟随父Mesh的PrimitiveType
- 简化了批量渲染，因为一个Mesh中的所有SubMesh使用相同的图元类型
- 减少了绘制调用的复杂性

### 2. Mesh现在是ResourceObject
- 实现了ResourceObject接口，支持资源管理
- 可以通过JSON文件加载
- 支持资源重载和缓存

### 3. 完整的资源加载系统
- 添加了MeshLoader用于从JSON加载网格
- 在ResourceTypes中注册了MESH类型
- 集成到GraphicsResourceManager和RenderResourceManager

### 4. Minecraft模型转换支持
- VanillaModelLoader: 基础的Minecraft模型加载
- MinecraftModelConverter: 高级转换器，支持缓存和批量转换
- VanillaModelResourceManager: 资源管理器，自动注册常用模型

## 使用方式

### 1. 程序化创建Mesh
```java
// 创建基础网格
Mesh mesh = MeshBuilder.create("my_mesh", PrimitiveType.TRIANGLES)
    .subMesh("main", 0, 3, vertexFormat)
    .vertices(/* vertex data */)
    .indices(/* index data */)
    .build();

// 编译为ModelMesh
ModelMesh modelMesh = MeshCompiler.compile(mesh);
```

### 2. 从JSON加载Mesh
```json
{
  "name": "example_mesh",
  "primitiveType": "triangles",
  "subMeshes": [
    {
      "name": "main",
      "id": 0,
      "vertexCount": 3,
      "vertexFormat": {
        "name": "PositionUV",
        "elements": [
          {"name": "position", "dataType": "vec3"},
          {"name": "texCoord", "dataType": "vec2"}
        ]
      },
      "vertices": [
        -0.5, -0.5, 0.0, 0.0, 0.0,
         0.5, -0.5, 0.0, 1.0, 0.0,
         0.0,  0.5, 0.0, 0.5, 1.0
      ],
      "indices": [0, 1, 2],
      "material": "default"
    }
  ]
}
```

### 3. 转换Minecraft模型
```java
// 转换单个模型
ModelMesh blockModel = VanillaModelLoader.loadBlockModel(
    new ResourceLocation("minecraft", "block/stone")
);

// 批量转换和缓存
MinecraftModelConverter converter = MinecraftModelConverter.getInstance();
ModelMesh cachedModel = converter.getOrCreateModelMesh(location);

// 资源管理器自动注册
VanillaModelResourceManager.getInstance().registerCommonBlockModels();
```

### 4. 资源系统集成
```java
// 通过资源管理器获取
GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
Optional<Mesh> mesh = resourceManager.getResource(ResourceTypes.MESH, 
    Identifier.of("my_mesh"));

// 动态注册Minecraft模型
VanillaModelResourceManager.getInstance().registerModel(
    "minecraft:stone", 
    new ResourceLocation("minecraft", "block/stone")
);
```

## 文件结构

```
src/main/java/rogo/sketch/render/
├── model/
│   ├── Mesh.java                    # 主网格类 (ResourceObject)
│   ├── MeshBone.java               # 骨骼类
│   ├── SubMesh.java                # 子网格类
│   ├── SubMeshInstance.java        # 编译后的子网格实例
│   ├── ModelMesh.java              # GPU就绪的模型网格
│   ├── MeshBuilder.java            # 网格构建器
│   ├── MeshCompiler.java           # 网格编译器
│   └── ModelMeshExample.java       # 使用示例
└── resource/
    ├── ResourceTypes.java          # 添加了MESH类型
    ├── GraphicsResourceManager.java # 注册了MeshLoader
    ├── loader/
    │   └── MeshLoader.java         # JSON加载器
    └── model/
        └── vanilla/                # Minecraft模型转换
            ├── VanillaModelLoader.java
            ├── MinecraftModelConverter.java
            └── VanillaModelResourceManager.java
```

## 资源路径

网格资源文件应放置在以下路径：
```
assets/[namespace]/render/resource/mesh/[mesh_name].json
```

例如：
```
assets/minecraft/render/resource/mesh/stone_block.json
```

## 优势

1. **统一的图元类型**: 简化了批量渲染逻辑
2. **资源管理集成**: 支持热重载、缓存和依赖管理
3. **Minecraft兼容性**: 无缝转换现有的Minecraft模型
4. **高性能**: 优化的GPU缓冲区管理和批量绘制
5. **灵活性**: 支持程序化创建和文件加载两种方式

这个重构保持了原有功能的同时，大大提升了系统的集成度和易用性。
