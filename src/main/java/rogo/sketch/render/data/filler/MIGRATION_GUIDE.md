# 数据填充系统新API迁移指南

## 📋 概述

Legacy compatibility methods确实不是必要的，它们是重构过程中为了临时兼容而添加的过渡方案。如果我们希望完全采用新设计的理念和模式，应该移除这些Legacy方法，并直接使用新的API设计。

## 🔄 核心设计理念变化

### 旧设计理念
- 混合了位置管理、数据写入、语义操作
- 依赖全局状态和模式切换
- API职责不清晰

### 新设计理念  
- **分离关注点**: DataFiller专注数据类型，VertexFiller专注语义
- **明确的填充模式**: 顺序填充 vs 随机访问填充
- **策略模式**: 统一不同存储后端

## 🚀 迁移方案

### 1. `end()` → `finish()`

#### 旧代码:
```java
filler.end();
```

#### 新代码:
```java
filler.finish();
```

**原理**: `finish()`是新设计中的标准完成方法，语义更明确。

---

### 2. `setVertexOffset()` + 顺序填充 → 直接顶点管理

#### 旧代码:
```java
filler.setVertexOffset(vertexOffset);
// 后续顺序填充...
```

#### 新代码:
```java
// 方案A: 使用vertex()方法直接跳转到指定顶点
filler.vertex(vertexOffset);
// 后续顺序填充...

// 方案B: 如果是批量操作，循环处理
for (int i = 0; i < vertexCount; i++) {
    filler.vertex(vertexOffset + i);
    // 填充数据...
    filler.nextVertex();
}
```

**原理**: 新设计中不需要全局偏移状态，直接使用`vertex(index)`跳转更明确。

---

### 3. `setIndexedMode()` + `fillVertexAt()` → 直接随机访问

#### 旧代码:
```java
filler.setIndexedMode(true);
filler.fillVertexAt(index, () -> {
    filler.position(x, y, z);
    filler.color(r, g, b, a);
});
```

#### 新代码:
```java
// 方案A: 使用专门的随机访问方法
filler.positionAt(index, x, y, z);
filler.colorAt(index, r, g, b, a);

// 方案B: 使用vertex()切换到目标顶点
filler.vertex(index);
filler.position(x, y, z);
filler.color(r, g, b, a);
```

**原理**: 新设计中不需要模式切换，直接使用相应的方法更简洁。

---

### 4. `writeFloatAt(vertexIndex, elementIndex, value)` → 字节偏移随机访问

#### 旧代码:
```java
filler.writeFloatAt(vertexIndex, elementIndex, value);
```

#### 新代码:
```java
// 方案A: 计算字节偏移 (如果需要底层控制)
long byteOffset = vertexIndex * format.getStride() + 
                  format.getElements().get(elementIndex).getOffset();
filler.putFloatAt(byteOffset, value);

// 方案B: 使用语义方法 (推荐)
switch (elementIndex) {
    case 0 -> filler.positionAt(vertexIndex, value, 0, 0); // 如果是位置的X分量
    case 3 -> filler.colorAt(vertexIndex, value, 0, 0, 1); // 如果是颜色的R分量
    // ... 其他元素
}

// 方案C: 切换到目标顶点后顺序写入
filler.vertex(vertexIndex);
for (int i = 0; i < elementIndex; i++) {
    // 跳过前面的元素...
}
filler.putFloat(value);
```

**原理**: 新设计使用字节偏移更加直接，或者使用语义方法更加清晰。

---

### 5. `floatValue()` → `putFloat()`

#### 旧代码:
```java
filler.floatValue(value);
```

#### 新代码:
```java
filler.putFloat(value);
```

**原理**: `putFloat()`符合新的命名规范，与GLSL数据类型对应。

---

### 6. `isIndexedMode()` 检查 → 能力查询

#### 旧代码:
```java
if (filler.isIndexedMode()) {
    filler.writeFloatAt(index, elementIndex, value);
} else {
    filler.floatValue(value);
}
```

#### 新代码:
```java
if (filler.supportsRandomAccess()) {
    long byteOffset = calculateOffset(index, elementIndex);
    filler.putFloatAt(byteOffset, value);
} else {
    filler.putFloat(value);
}
```

**原理**: 新设计基于能力查询而不是模式状态，更加灵活。

## 📚 完整迁移示例

### AsyncVertexFiller 迁移示例

#### 旧代码:
```java
public void fillInstance(VertexFiller filler, GraphicsInformation info) {
    int vertexOffset = info.getVertexOffset();
    filler.setVertexOffset(vertexOffset);
    
    if (info.hasModelMesh()) {
        fillFromModelMesh(filler, info);
    }
}

private void fillFromModelMesh(VertexFiller filler, GraphicsInformation info) {
    filler.setIndexedMode(true);
    for (int i = 0; i < vertexCount; i++) {
        filler.fillVertexAt(i, () -> {
            filler.position(x, y, z);
            filler.color(r, g, b, a);
        });
    }
}
```

#### 新代码:
```java
public void fillInstance(VertexFiller filler, GraphicsInformation info) {
    long vertexOffset = info.getVertexOffset();
    
    if (info.hasModelMesh()) {
        fillFromModelMesh(filler, info, vertexOffset);
    }
}

private void fillFromModelMesh(VertexFiller filler, GraphicsInformation info, long vertexOffset) {
    // 方案A: 批量随机访问 (如果支持)
    if (filler.supportsRandomAccess()) {
        for (int i = 0; i < vertexCount; i++) {
            long vertexIndex = vertexOffset + i;
            filler.positionAt(vertexIndex, x, y, z);
            filler.colorAt(vertexIndex, r, g, b, a);
        }
    } else {
        // 方案B: 顺序填充
        filler.vertex(vertexOffset);
        for (int i = 0; i < vertexCount; i++) {
            filler.position(x, y, z);
            filler.color(r, g, b, a);
            filler.nextVertex();
        }
    }
}
```

### InstancedRenderingExample 迁移示例

#### 旧代码:
```java
if (filler.isIndexedMode()) {
    filler.positionAt(index, instance.position[0], instance.position[1], instance.position[2]);
    filler.writeFloatAt(index, 3, instance.scale);
    filler.colorAt(index, 4, instance.color[0], instance.color[1], instance.color[2], instance.color[3]);
} else {
    filler.position(instance.position[0], instance.position[1], instance.position[2])
          .floatValue(instance.scale)
          .color(instance.color[0], instance.color[1], instance.color[2], instance.color[3]);
}
```

#### 新代码:
```java
// 统一使用语义方法，让具体实现决定如何处理
filler.vertex(index)  // 切换到目标顶点
      .position(instance.position[0], instance.position[1], instance.position[2])
      .putFloat(instance.scale)  // 直接使用新API
      .color(instance.color[0], instance.color[1], instance.color[2], instance.color[3])
      .nextVertex();

// 或者如果确实需要随机访问:
if (filler.supportsRandomAccess()) {
    filler.positionAt(index, instance.position[0], instance.position[1], instance.position[2]);
    // 计算scale元素的字节偏移
    long scaleOffset = index * filler.getFormat().getStride() + scaleElementOffset;
    filler.putFloatAt(scaleOffset, instance.scale);
    filler.colorAt(index, instance.color[0], instance.color[1], instance.color[2], instance.color[3]);
}
```

## 🛠️ 实际迁移步骤

### 步骤1: 移除Legacy方法
从VertexFiller中删除所有标记为`@Deprecated`或在Legacy compatibility section中的方法。

### 步骤2: 更新调用方代码
按照上述迁移方案逐个更新所有使用旧API的地方。

### 步骤3: 简化设计
移除不必要的状态变量如`indexedMode`, `vertexOffset`等。

### 步骤4: 优化性能
利用新设计的优势，选择最适合的填充模式。

## ✅ 迁移后的优势

1. **代码更清晰**: 不再有模式切换的复杂性
2. **性能更好**: 直接使用最适合的API，避免兼容层开销
3. **类型安全**: 完整的GLSL数据类型支持
4. **易于理解**: 每个方法的职责更加明确
5. **易于扩展**: 基于能力查询的设计更灵活

## 🎯 推荐的新使用模式

### 顺序填充模式 (推荐用于大多数场景)
```java
VertexFiller filler = new VertexFiller(format, PrimitiveType.TRIANGLES);

// 填充第一个顶点
filler.position(0, 0, 0)
      .normal(0, 0, 1)
      .uv(0, 0)
      .color(1, 0, 0, 1)
      .nextVertex();

// 填充第二个顶点  
filler.position(1, 0, 0)
      .normal(0, 0, 1)
      .uv(1, 0)
      .color(0, 1, 0, 1)
      .nextVertex();

filler.finish();
```

### 随机访问模式 (用于需要非顺序访问的场景)
```java
VertexFiller filler = new VertexFiller(format, PrimitiveType.TRIANGLES);

// 直接访问特定顶点
filler.positionAt(0, 0, 0, 0);
filler.positionAt(1, 1, 0, 0);
filler.positionAt(2, 0.5f, 1, 0);

// 或者切换到特定顶点后顺序填充
filler.vertex(0).position(0, 0, 0).normal(0, 0, 1);
filler.vertex(1).position(1, 0, 0).normal(0, 0, 1);

filler.finish();
```

这样的迁移会让代码更加清晰和高效，完全符合新设计的理念。
