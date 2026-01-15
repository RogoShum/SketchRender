# Data Builder 使用指南

本文档详细介绍 `src/main/java/rogo/sketch/render/data/builder` 包中所有类的功能和使用方法。

## 目录

1. [概述](#概述)
2. [核心接口](#核心接口)
3. [实现类详解](#实现类详解)
4. [使用场景](#使用场景)
5. [完整示例](#完整示例)

---

## 概述

本包提供了一套统一的数据缓冲区构建 API，用于在 OpenGL/Vulkan 渲染中填充各种类型的数据：

- **顶点数据** (Vertex Data) - 位置、法线、纹理坐标等
- **索引数据** (Index Data) - 顶点索引
- **SSBO 数据** (Shader Storage Buffer Object) - 着色器存储缓冲
- **Uniform 数据** - 统一变量

设计目标：
- 统一的 API，支持多种后端（ByteBuffer、直接内存地址、VBO 映射）
- 类型安全的 GLSL 数据类型支持
- 自动扩容能力
- 高性能的直接内存访问

---

## 核心接口

### 1. DataBufferWriter

**基础数据写入接口**，提供流式 API 和方法链式调用。

**核心方法：**

```java
// 标量类型
DataBufferWriter putFloat(float value)
DataBufferWriter putInt(int value)
DataBufferWriter putUInt(int value)
DataBufferWriter putByte(byte value)
DataBufferWriter putShort(short value)
DataBufferWriter putDouble(double value)
DataBufferWriter putLong(long value)

// GLSL 向量类型
DataBufferWriter putVec2(float x, float y)
DataBufferWriter putVec3(float x, float y, float z)
DataBufferWriter putVec4(float x, float y, float z, float w)
DataBufferWriter putIVec2(int x, int y)
DataBufferWriter putIVec3(int x, int y, int z)
DataBufferWriter putIVec4(int x, int y, int z, int w)

// 字节向量（常用于颜色）
DataBufferWriter putVec4ub(int r, int g, int b, int a)  // RGBA 作为 unsigned bytes

// 矩阵类型
DataBufferWriter putMat2(Matrix2f matrix)
DataBufferWriter putMat3(Matrix3f matrix)
DataBufferWriter putMat4(Matrix4f matrix)

// 游标管理
void advance(int bytes)                    // 前进指定字节
long getWriteOffset()                      // 获取当前写入偏移
void setWriteOffset(long offset)           // 设置写入位置
void ensureCapacity(int additionalBytes)  // 确保容量

// 随机访问（如果支持）
void putFloatAt(long byteOffset, float value)
boolean supportsRandomAccess()
```

**特点：**
- 所有方法返回 `DataBufferWriter`，支持链式调用
- 支持 JOML 向量/矩阵类型
- 提供默认实现处理无符号类型

---

### 2. DataBuilder

**高级数据构建接口**，在 DataBufferWriter 基础上增加了格式感知和语义。

**额外方法：**

```java
// 获取格式
DataFormat getFormat()

// 字节数组写入
DataBuilder putBytes(byte[] bytes)
DataBuilder putBytes(ByteBuffer buffer)

// 游标管理
void skip(int bytes)   // 跳过字节（等同于写入垃圾/零）
long offset()          // 获取偏移
void reset()           // 重置到开头
void finish()          // 完成构建
```

**与 DataBufferWriter 的区别：**
- `DataBufferWriter` 是底层接口，关注字节写入
- `DataBuilder` 是高层接口，关注数据语义和格式

---

### 3. VertexProcessor

**顶点数据处理器接口**，用于在顶点构建过程中插入自定义逻辑。

**回调方法：**

```java
void onStartBuild(VertexDataBuilder builder)      // 构建开始
void onStartVertex(VertexDataBuilder builder, int vertexIndex)  // 顶点开始
void onEndVertex(VertexDataBuilder builder, int vertexIndex)    // 顶点结束
void onFinish(VertexDataBuilder builder)          // 构建结束
```

**用途：**
- 顶点排序（按深度、材质等）
- 索引生成
- 自定义命令生成

---

## 实现类详解

### 1. MemoryBufferWriter

使用 **ByteBuffer**（堆内存或直接内存）作为后端的写入器。

**构造方法：**

```java
// 使用现有 ByteBuffer（不自动扩容）
MemoryBufferWriter(ByteBuffer buffer)

// 创建自动扩容的 Direct Buffer（初始 4KB）
MemoryBufferWriter(int initialCapacity)
```

**特点：**
- ✅ 自动扩容（2倍增长策略）
- ✅ 支持随机访问
- ✅ 线程不安全（需外部同步）
- ⚠️ 扩容时会重新分配内存

**使用示例：**

```java
// 创建 1MB 的自动扩容缓冲
MemoryBufferWriter writer = new MemoryBufferWriter(1024 * 1024);

// 写入顶点数据
writer.putVec3(0.0f, 1.0f, 0.0f)    // 位置
      .putVec3(0.0f, 1.0f, 0.0f)    // 法线
      .putVec2(0.5f, 0.5f);         // UV

// 获取构建好的 ByteBuffer
ByteBuffer buffer = writer.getBuffer();
buffer.flip();  // 准备读取

// 上传到 OpenGL
glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

// 使用完毕后释放
writer.dispose();
```

---

### 2. AddressBufferWriter

使用 **直接内存地址**（Unsafe）的写入器，适用于：

- **SSBO 指针** - Shader Storage Buffer Object 的映射地址
- **Mapped VBO** - 通过 `glMapBuffer` 映射的顶点缓冲
- **Off-heap 内存** - 堆外直接内存

**构造方法：**

```java
// 使用原始地址
AddressBufferWriter(long address, long capacity)

// 使用 DataResourceObject（支持自动扩容）
AddressBufferWriter(DataResourceObject dataResource)
```

**特点：**
- ✅ 零拷贝，性能最高
- ✅ 支持 ResizeCallback 实现动态扩容
- ✅ 支持随机访问
- ⚠️ 需要手动管理内存生命周期
- ⚠️ 地址无效会导致崩溃

**使用示例：**

```java
// 假设有一个 SSBO 已映射到内存
long ssboAddress = glMapBufferRange(GL_SHADER_STORAGE_BUFFER, ...);
long ssboSize = 1024 * 1024;

// 创建写入器
AddressBufferWriter writer = new AddressBufferWriter(ssboAddress, ssboSize);

// 写入数据
writer.putFloat(1.0f).putFloat(2.0f).putFloat(3.0f);

// 获取当前偏移
long written = writer.getOffset();

// 完成后解除映射
glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
```

**支持 ResizeCallback：**

```java
AddressBufferWriter writer = new AddressBufferWriter(ssboAddress, ssboSize);
writer.setResizeCallback(newCapacity -> {
    // 重新分配更大的缓冲
    long newAddress = reallocSSBO(newCapacity);
    return newAddress;
});

// 写入超过容量时自动触发回调
writer.putFloat(...);  // 如果超出容量，会先调用 resize
```

---

### 3. MemoryDataBuilder

实现 `DataBuilder` 接口，使用 ByteBuffer 作为后端。

**构造方法：**

```java
// 创建自动扩容的 Direct Buffer
MemoryDataBuilder(DataFormat format, int initialCapacity)

// 包装现有 ByteBuffer
MemoryDataBuilder(DataFormat format, ByteBuffer existingBuffer)
```

**特点：**
- 格式感知
- 自动扩容
- 可获取只读切片

**使用示例：**

```java
DataFormat format = new DataFormat.Builder()
    .addElement("position", DataType.VEC3)
    .addElement("normal", DataType.VEC3)
    .addElement("texCoord", DataType.VEC2)
    .build();

MemoryDataBuilder builder = new MemoryDataBuilder(format, 4096);

builder.putVec3(0, 0, 0)      // position
       .putVec3(0, 1, 0)      // normal
       .putVec2(0, 0);        // texCoord

ByteBuffer data = builder.getReadableBuffer();
```

---

### 4. AddressDataBuilder

实现 `DataBuilder` 接口，直接写入内存地址。

**构造方法：**

```java
AddressDataBuilder(DataFormat format, long address, long capacity)
```

**特点：**
- 无边界检查（性能优先）
- 适用于已知容量的场景
- 手动偏移管理

**使用示例：**

```java
long mappedAddress = glMapBuffer(GL_ARRAY_BUFFER, ...);
DataFormat format = ...;

AddressDataBuilder builder = new AddressDataBuilder(format, mappedAddress, bufferSize);

builder.putVec3(0, 0, 0)
       .putVec3(0, 1, 0)
       .putVec2(0.5f, 0.5f);

builder.setOffset(0);  // 重置偏移重新写入
```

---

### 5. VertexDataBuilder

**顶点数据构建器**，提供顶点级别的语义和格式感知。

**构造方法：**

```java
// 使用现有 writer
VertexDataBuilder(DataBufferWriter writer, DataFormat format, PrimitiveType primitiveType)

// 创建默认 MemoryBufferWriter
VertexDataBuilder(DataFormat format, PrimitiveType primitiveType)
```

**核心方法：**

```java
// 顶点提交
VertexDataBuilder vertex()      // 开始顶点
VertexDataBuilder put(float x)  // 智能写入（自动转换类型）
VertexDataBuilder put(float x, float y)
VertexDataBuilder put(float x, float y, float z)
void endVertex()                // 结束顶点

// 原始写入（手动控制）
VertexDataBuilder putFloat(float f)
VertexDataBuilder putVec3(float x, float y, float z)

// 配置
VertexDataBuilder enableSorting(VertexSorting strategy)
VertexDataBuilder addProcessor(VertexProcessor processor)

// 完成构建
void finish()
void reset()

// 访问器
int getVertexCount()
PrimitiveType getPrimitiveType()
DataFormat getFormat()
```

**智能 Put 方法：**

`put(float, float, float)` 等智能方法会根据当前 DataElement 的类型自动转换：

```java
// 如果当前元素是 VEC3B（归一化字节向量）
builder.put(1.0f, 0.0f, 0.0f);  // 自动转换为: 127, 0, 0

// 如果当前元素是 VEC3S（归一化短向量）
builder.put(1.0f, 0.0f, 0.0f);  // 自动转换为: 32767, 0, 0
```

**使用示例（标准方式）：**

```java
// 定义顶点格式
DataFormat format = new DataFormat.Builder()
    .addElement("position", DataType.VEC3)
    .addElement("normal", DataType.VEC3)
    .addElement("color", DataType.VEC4UB)  // RGBA 作为 unsigned bytes
    .build();

// 创建构建器
VertexDataBuilder builder = new VertexDataBuilder(format, PrimitiveType.TRIANGLES);

// 提交顶点
builder.vertex()
       .put(0.0f, 1.0f, 0.0f)    // position
       .put(0.0f, 1.0f, 0.0f)    // normal
       .putColorbyte(255, 255, 255, 255)  // color
       .endVertex();

builder.vertex()
       .put(-1.0f, 0.0f, 0.0f)
       .put(0.0f, 0.0f, 1.0f)
       .putColorbyte(255, 0, 0, 255)
       .endVertex();

// 完成构建
builder.finish();

System.out.println("Vertex count: " + builder.getVertexCount());
```

---

## 使用场景

### 场景 1：使用 SSBO 填充数据

SSBO（Shader Storage Buffer Object）允许着色器读写大块数据。

**步骤：**

1. 创建 SSBO
2. 映射到内存地址
3. 使用 `AddressBufferWriter` 或 `AddressDataBuilder` 写入
4. 解除映射

**示例：**

```java
// 1. 创建 SSBO
int ssbo = glGenBuffers();
glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
glBufferData(GL_SHADER_STORAGE_BUFFER, 1024 * 1024, GL_DYNAMIC_DRAW);

// 2. 映射到内存
long address = glMapBufferRange(GL_SHADER_STORAGE_BUFFER, 0, 1024 * 1024,
    GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

// 3. 写入数据
AddressBufferWriter writer = new AddressBufferWriter(address, 1024 * 1024);

// 写入粒子数据
for (int i = 0; i < particleCount; i++) {
    writer.putVec3(particle.x, particle.y, particle.z)  // 位置
          .putVec3(particle.vx, particle.vy, particle.vz)  // 速度
          .putFloat(particle.life)  // 生命周期
          .putFloat(particle.size); // 大小
}

// 4. 解除映射
glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);

// 5. 绑定到着色器
glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, ssbo);
```

---

### 场景 2：使用 ByteBuffer 填充顶点数据

适用于小型网格或动态网格。

**示例（使用 VertexDataBuilder）：**

```java
// 定义顶点格式
DataFormat format = new DataFormat.Builder()
    .addElement("position", DataType.VEC3)
    .addElement("normal", DataType.VEC3)
    .addElement("texCoord", DataType.VEC2)
    .build();

// 创建构建器（自动创建 MemoryBufferWriter）
VertexDataBuilder builder = new VertexDataBuilder(format, PrimitiveType.TRIANGLES);

// 构建三角形
builder.vertex()
       .put(0.0f, 1.0f, 0.0f)   // position
       .put(0.0f, 0.0f, 1.0f)   // normal
       .put(0.5f, 1.0f)         // texCoord
       .endVertex();

builder.vertex()
       .put(-1.0f, 0.0f, 0.0f)
       .put(0.0f, 0.0f, 1.0f)
       .put(0.0f, 0.0f)
       .endVertex();

builder.vertex()
       .put(1.0f, 0.0f, 0.0f)
       .put(0.0f, 0.0f, 1.0f)
       .put(1.0f, 0.0f)
       .endVertex();

// 完成构建
builder.finish();

// 获取底层的 MemoryBufferWriter
MemoryBufferWriter writer = (MemoryBufferWriter) builder.getWriter();
ByteBuffer vertexBuffer = writer.getBuffer();
vertexBuffer.flip();

// 上传到 VBO
int vbo = glGenBuffers();
glBindBuffer(GL_ARRAY_BUFFER, vbo);
glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

// 清理
writer.dispose();
```

---

### 场景 3：使用索引缓冲

索引缓冲允许顶点重用，减少内存占用。

**示例：**

```java
// 先构建顶点数据
VertexDataBuilder vertexBuilder = new VertexDataBuilder(vertexFormat, PrimitiveType.TRIANGLES);
// ... 添加顶点 ...

vertexBuilder.finish();
ByteBuffer vertexData = ((MemoryBufferWriter) vertexBuilder.getWriter()).getBuffer();
vertexData.flip();

// 构建索引数据
MemoryBufferWriter indexWriter = new MemoryBufferWriter(4096);

int[] indices = {
    0, 1, 2,  // 第一个三角形
    2, 3, 0   // 第二个三角形
};

for (int index : indices) {
    indexWriter.putInt(index);  // 使用 int 索引
}

ByteBuffer indexData = indexWriter.getBuffer();
indexData.flip();

// 上传到 OpenGL
int vbo = glGenBuffers();
glBindBuffer(GL_ARRAY_BUFFER, vbo);
glBufferData(GL_ARRAY_BUFFER, vertexData, GL_STATIC_DRAW);

int ebo = glGenBuffers();
glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, GL_STATIC_DRAW);

// 绘制
glBindBuffer(GL_ARRAY_BUFFER, vbo);
glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
```

---

### 场景 4：使用 VBO 直接映射

适用于动态更新大量数据（如粒子系统）。

**示例：**

```java
// 创建并映射 VBO
int vbo = glGenBuffers();
glBindBuffer(GL_ARRAY_BUFFER, vbo);
glBufferData(GL_ARRAY_BUFFER, bufferSize, GL_DYNAMIC_DRAW);

long mappedAddress = glMapBufferRange(GL_ARRAY_BUFFER, 0, bufferSize,
    GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

// 使用 AddressBufferWriter 写入
AddressBufferWriter writer = new AddressBufferWriter(mappedAddress, bufferSize);

for (Particle p : particles) {
    writer.putVec3(p.x, p.y, p.z)
          .putVec3(p.vx, p.vy, p.vz)
          .putFloat(p.life)
          .putFloat(p.size);
}

// 解除映射
glUnmapBuffer(GL_ARRAY_BUFFER);

// 绑定并绘制
glBindBuffer(GL_ARRAY_BUFFER, vbo);
setupVertexAttributes(...);
glDrawArrays(GL_POINTS, 0, particleCount);
```

---

### 场景 5：不使用索引缓冲

直接绘制，适用于简单几何体。

**示例：**

```java
// 构建顶点数据（包含重复顶点）
VertexDataBuilder builder = new VertexDataBuilder(format, PrimitiveType.TRIANGLES);

// 三角形 1
builder.vertex().put(-1, 0, 0).put(0, 1, 0).endVertex();
builder.vertex().put(0, 1, 0).put(0, 1, 0).endVertex();
builder.vertex().put(1, 0, 0).put(0, 1, 0).endVertex();

// 三角形 2（共享顶点会重复存储）
builder.vertex().put(0, 1, 0).put(0, 1, 0).endVertex();
builder.vertex().put(1, 0, 0).put(0, 1, 0).endVertex();
builder.vertex().put(0, 0, 1).put(0, 1, 0).endVertex();

builder.finish();

// 直接上传并绘制
MemoryBufferWriter writer = (MemoryBufferWriter) builder.getWriter();
ByteBuffer buffer = writer.getBuffer();
buffer.flip();

glBindBuffer(GL_ARRAY_BUFFER, vbo);
glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

// 使用 glDrawArrays 而不是 glDrawElements
glDrawArrays(GL_TRIANGLES, 0, builder.getVertexCount());
```

---

## 完整示例

### 示例：构建一个立方体网格

```java
import rogo.sketch.render.data.builder.*;
import rogo.sketch.render.data.format.*;
import rogo.sketch.render.data.PrimitiveType;
import org.lwjgl.opengl.GL45.*;

public class CubeBuilder {
    public static void main(String[] args) {
        // 1. 定义顶点格式
        DataFormat format = new DataFormat.Builder()
            .addElement("position", DataType.VEC3)
            .addElement("normal", DataType.VEC3)
            .addElement("texCoord", DataType.VEC2)
            .build();

        // 2. 创建顶点构建器
        VertexDataBuilder builder = new VertexDataBuilder(format, PrimitiveType.TRIANGLES);

        // 3. 添加立方体的 24 个顶点（每面 4 个，无共享）
        // 前面 (Z+)
        builder.vertex().put(-1, -1, 1).put(0, 0, 1).put(0, 0).endVertex();
        builder.vertex().put(1, -1, 1).put(0, 0, 1).put(1, 0).endVertex();
        builder.vertex().put(1, 1, 1).put(0, 0, 1).put(1, 1).endVertex();
        builder.vertex().put(-1, 1, 1).put(0, 0, 1).put(0, 1).endVertex();

        // 后面 (Z-)
        builder.vertex().put(1, -1, -1).put(0, 0, -1).put(0, 0).endVertex();
        builder.vertex().put(-1, -1, -1).put(0, 0, -1).put(1, 0).endVertex();
        builder.vertex().put(-1, 1, -1).put(0, 0, -1).put(1, 1).endVertex();
        builder.vertex().put(1, 1, -1).put(0, 0, -1).put(0, 1).endVertex();

        // ... 其他面 ...

        builder.finish();

        // 4. 构建索引（36 个索引：12 个三角形）
        MemoryBufferWriter indexWriter = new MemoryBufferWriter(4096);

        int[] indices = {
            // 前面
            0, 1, 2, 0, 2, 3,
            // 后面
            4, 5, 6, 4, 6, 7,
            // ... 其他面 ...
        };

        for (int index : indices) {
            indexWriter.putInt(index);
        }

        // 5. 上传到 OpenGL
        MemoryBufferWriter vertexWriter = (MemoryBufferWriter) builder.getWriter();
        ByteBuffer vertexBuffer = vertexWriter.getBuffer();
        vertexBuffer.flip();

        ByteBuffer indexBuffer = indexWriter.getBuffer();
        indexBuffer.flip();

        // 创建 VAO
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // 创建并填充 VBO
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // 设置顶点属性
        long stride = format.getStride();
        long positionOffset = format.getElement(0).getOffset();
        long normalOffset = format.getElement(1).getOffset();
        long texCoordOffset = format.getElement(2).getOffset();

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, positionOffset);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, normalOffset);

        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, texCoordOffset);

        // 创建并填充 EBO
        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);

        // 解绑
        glBindVertexArray(0);

        // 6. 绘制
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        // 7. 清理
        vertexWriter.dispose();
        indexWriter.dispose();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
    }
}
```

---

## 性能建议

### 1. 选择合适的 Writer

| 场景 | 推荐实现 | 原因 |
|------|---------|------|
| 静态网格，一次性上传 | `MemoryBufferWriter` | 简单，自动扩容 |
| 动态更新顶点数据 | `AddressBufferWriter` + `glMapBuffer` | 零拷贝 |
| SSBO 数据填充 | `AddressBufferWriter` | 直接内存访问 |
| 小规模数据 | `MemoryDataBuilder` | 格式感知，易于使用 |
| 大规模顶点 | `VertexDataBuilder` + 排序 | 支持排序，批量处理 |

### 2. 优化技巧

**批量上传：**
```java
// ❌ 差：每个顶点单独上传
for (Vertex v : vertices) {
    glBufferSubData(GL_ARRAY_BUFFER, offset, v.data);
}

// ✅ 好：批量上传
builder.buildAllVertices();
glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
```

**使用持久映射（Persistent Mapping）：**
```java
// 创建持久映射的缓冲
long flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
glBufferStorage(GL_ARRAY_BUFFER, size, 0, flags);
long address = glMapBufferRange(GL_ARRAY_BUFFER, 0, size, flags);

AddressBufferWriter writer = new AddressBufferWriter(address, size);
// 每帧直接写入，无需重新映射
```

**启用顶点排序：**
```java
// 对于透明渲染，按深度排序
builder.enableSorting(VertexSorting.BY_DEPTH);
```

### 3. 内存管理

**及时释放 Direct Buffer：**
```java
MemoryBufferWriter writer = new MemoryBufferWriter(capacity);
try {
    // 使用 writer
} finally {
    writer.dispose();  // 释放直接内存
}
```

**使用 try-with-resources（如果实现了 AutoCloseable）：**
```java
// 可以修改 MemoryBufferWriter 实现 AutoCloseable
try (MemoryBufferWriter writer = new MemoryBufferWriter(capacity)) {
    // 自动释放
}
```

---

## 常见问题

### Q: 如何选择 DataBufferWriter 还是 DataBuilder？

**A:**
- 使用 `DataBufferWriter`：如果你只需要原始字节写入，不需要格式感知
- 使用 `DataBuilder`：如果你需要格式信息和自动类型转换
- 使用 `VertexDataBuilder`：如果你在构建顶点数据（最常用）

### Q: putVec3() 和智能 put() 有什么区别？

**A:**
- `putVec3(x, y, z)`：固定写入 3 个 float，不关心格式
- `put(x, y, z)`：根据当前 DataElement 类型自动转换

```java
// putVec3 总是写入 12 字节（3 个 float）
writer.putVec3(1.0f, 0.0f, 0.0f);

// put 可能写入 3 字节（如果是 VEC3B）
builder.put(1.0f, 0.0f, 0.0f);  // 如果当前元素是 VEC3B
```

### Q: 如何处理字节序（Endianness）？

**A:**
- `MemoryBufferWriter` 自动设置为本地字节序（`ByteOrder.nativeOrder()`）
- `AddressBufferWriter` 使用 LWJGL 的 `memPut*` 方法，自动处理

### Q: 可以多线程写入吗？

**A:**
- **不可以**。所有 Writer/Builder 都是非线程安全的。
- 如需多线程，请在外部同步，或为每个线程创建独立的 Writer，最后合并数据。

### Q: 如何调试写入的数据？

**A:**
```java
// 获取当前偏移
long offset = writer.getWriteOffset();

// 使用 MemoryBufferWriter 查看内容
MemoryBufferWriter memWriter = (MemoryBufferWriter) writer;
ByteBuffer buffer = memWriter.getBuffer();

// 备份当前位置
int oldPosition = buffer.position();

// 查看特定偏移的数据
buffer.position((int)offset);
float value = buffer.getFloat();

// 恢复位置
buffer.position(oldPosition);
```

---

## 总结

本包提供了三层 API：

1. **DataBufferWriter** - 底层字节写入接口
2. **DataBuilder** - 中层格式感知接口
3. **VertexDataBuilder** - 高层顶点语义接口

选择适合你场景的层级，遵循以下原则：

- **简单场景**：`VertexDataBuilder` + `MemoryBufferWriter`
- **高性能场景**：`AddressBufferWriter` + 映射缓冲
- **SSBO/Uniform**：`AddressDataBuilder` 或 `AddressBufferWriter`

所有实现都支持：
- ✅ 链式调用
- ✅ 自动扩容（可选）
- ✅ 类型安全的 GLSL 数据类型
- ✅ 随机访问（部分实现）
