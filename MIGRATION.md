# Migration Guide: Vertex Data Filling and Batching Improvements

This document outlines the major architectural changes introduced to the vertex data filling and batching system, and provides guidance for migrating existing code.

## Overview of Changes

### Phase 1: Strategy Pattern for Batch Processing
- **New**: Extensible strategy pattern via `BatchProcessingStrategy` interface
- **Benefits**: Third-party mods can register custom batch processing strategies
- **Files**:
  - `BatchProcessingStrategy.java`
  - `BatchProcessingStrategyRegistry.java`
  - `StandardBatchStrategy.java`
  - `InstancedBatchStrategy.java`

### Phase 2: Component-Based VBO Architecture
- **New**: `VertexBufferKey` with `ComponentSpec` list instead of `isInstanced()` boolean
- **New**: `VBOComponent` unifies VBO, format, and configuration
- **New**: `InstancedVertexLayout` supports multiple instance formats at different bindings
- **Benefits**: No hardcoded binding semantics, support for multi-component instancing
- **Files**:
  - `VertexBufferKey.java` (redesigned)
  - `VBOComponent.java`
  - `VertexResource.java` (modular design)
  - `InstancedVertexLayout.java` (multi-format support)
  - `VertexResourceManager.java` (updated API)

### Phase 3: BakedMesh Zero-Copy Integration
- **New**: `BakedTypeMesh` interface methods for zero-copy reference
- **Benefits**: Direct VBO reference without data copying for static geometry
- **Files**:
  - `BakedTypeMesh.java` (new methods)
  - `BakedMesh.java` (implemented zero-copy methods)

### Phase 4: Updated Mesh Filling APIs
- **New**: `DynamicTypeMesh.fill(VertexDataBuilder, Matrix4f)`
- **Deprecated**: `DynamicTypeMesh.fill(VertexFiller)`
- **Benefits**: Direct builder usage, support for transform application
- **Files**:
  - `DynamicTypeMesh.java`
  - `StandardBatchStrategy.java`
  - `InstancedBatchStrategy.java`

### Phase 5: Legacy API Deprecation
- **Deprecated**: `VertexFiller` class (use `VertexDataBuilder` instead)
- **Deprecated**: Various filler strategy classes (removed)
- **Files**:
  - `VertexFiller.java` (@Deprecated)
  - `SSBOFiller.java` (minimal utility, kept for compatibility)

---

## Migration Guide

### 1. Migrating from `VertexBufferKey(RenderParameter, InstancedVertexLayout)` Record

#### Old API:
```java
VertexBufferKey key = new VertexBufferKey(param, layout);
```

#### New API:
```java
// For non-instanced rendering
VertexBufferKey key = VertexBufferKey.forStandard(param);

// For instanced rendering with single format
List<DataFormat> formats = List.of(layout.dataFormat());
VertexBufferKey key = VertexBufferKey.forInstanced(param, formats);

// For instanced rendering with multi-format layout
VertexBufferKey key = VertexBufferKey.forInstanced(param, layout);

// For BakedMesh with instancing (zero-copy)
VertexBufferKey key = VertexBufferKey.forBakedInstanced(param, formats);

// For custom configurations
VertexBufferKey key = VertexBufferKey.custom(param, List.of(
    VertexBufferKey.ComponentSpec.mutable(0, staticFormat, false),
    VertexBufferKey.ComponentSpec.mutable(1, transformFormat, true),
    VertexBufferKey.ComponentSpec.mutable(2, colorFormat, true)
));
```

### 2. Migrating from `isInstanced()` to `hasInstancing()`

#### Old API:
```java
if (key.isInstanced()) {
    // Handle instanced rendering
}
```

#### New API:
```java
if (key.hasInstancing()) {
    // Handle instanced rendering
}

// Or check for specific components
if (key.components().stream().anyMatch(ComponentSpec::isInstanced)) {
    // Has instanced components
}
```

### 3. Migrating from `BINDING_MAIN` / `BINDING_INSTANCE` Constants

#### Old API:
```java
resource.upload(VertexResource.BINDING_MAIN, staticBuilder);
resource.upload(VertexResource.BINDING_INSTANCE, instanceBuilder);
```

#### New API:
```java
resource.upload(0, staticBuilder);   // Binding 0: static geometry
resource.upload(1, instanceBuilder); // Binding 1: first instance format
resource.upload(2, colorBuilder);    // Binding 2: second instance format
// etc.
```

### 4. Migrating from `VertexFiller` to `VertexDataBuilder`

#### Old API:
```java
VertexFiller filler = new VertexFiller(format, primitiveType);
filler.position(x, y, z).color(r, g, b, a).nextVertex();
```

#### New API:
```java
VertexDataBuilder builder = new VertexDataBuilder(format, primitiveType);
builder.putVec3(x, y, z).putColor(r, g, b, a).endVertex();
```

### 5. Migrating `DynamicTypeMesh` Implementation

#### Old API:
```java
public class MyMesh implements DynamicTypeMesh {
    @Override
    public void fill(VertexFiller filler) {
        filler.position(x, y, z).nextVertex();
    }
}
```

#### New API:
```java
public class MyMesh implements DynamicTypeMesh {
    @Override
    public void fill(VertexDataBuilder builder, Matrix4f transform) {
        // Apply transform if provided
        if (transform != null) {
            Vector3f pos = new Vector3f(x, y, z);
            transform.transformPosition(pos);
            builder.putVec3(pos.x, pos.y, pos.z);
        } else {
            builder.putVec3(x, y, z);
        }
        builder.endVertex();
    }

    @Override
    @Deprecated
    public void fill(VertexFiller filler) {
        // Legacy support - delegate to new API
        fill(new VertexDataBuilder(getVertexFormat(), getPrimitiveType()), null);
    }
}
```

### 6. Migrating `InstancedLayoutProvider` to Multi-Component

#### Old API (Single Component):
```java
public class MyInstance implements InstancedLayoutProvider {
    @Override
    public InstancedVertexLayout getInstancedVertexLayout() {
        return new InstancedVertexLayout(transformFormat);
    }

    @Override
    public void fillInstanceData(VertexDataBuilder builder) {
        builder.putMatrix4f(transform);
        builder.endVertex();
    }
}
```

#### New API (Multi-Component):
```java
public class MyInstance implements InstancedLayoutProvider {
    private final Matrix4f transform = new Matrix4f();
    private final Vector4f color = new Vector4f(1, 1, 1, 1);
    private final Vector3f offset = new Vector3f(0, 0, 0);

    @Override
    public InstancedVertexLayout getInstancedVertexLayout() {
        return InstancedVertexLayout.builder()
            .addFormat(1, transformFormat)  // Binding 1: mat4
            .addFormat(2, colorFormat)      // Binding 2: vec4
            .addFormat(3, offsetFormat)     // Binding 3: vec3
            .build();
    }

    @Override
    public void fillInstanceData(int bindingPoint, VertexDataBuilder builder) {
        switch (bindingPoint) {
            case 1 -> builder.putMatrix4f(transform);
            case 2 -> builder.putVec4(color.x, color.y, color.z, color.w);
            case 3 -> builder.putVec3(offset.x, offset.y, offset.z);
        }
        builder.endVertex();
    }
}
```

### 7. Implementing Zero-Copy BakedMesh

```java
public class MyBakedMesh implements BakedTypeMesh {
    private final VertexResource sourceResource;
    private final int vertexOffset;

    @Override
    public VertexResource getSourceResource() {
        return sourceResource;
    }

    @Override
    public int getMainVBOHandle() {
        var component = sourceResource.getComponent(0);
        return component != null ? component.getVboHandle() : 0;
    }

    @Override
    public int getSourceVertexOffset() {
        return vertexOffset;
    }

    @Override
    public boolean supportsZeroCopy() {
        return getSourceResource() != null;
    }
}
```

---

## Creating Custom Batch Strategies

Third-party mods can register custom batch processing strategies:

```java
public class MyCustomStrategy implements BatchProcessingStrategy {
    @Override
    public GeometryBatchProcessor.ProcessorResult processBatches(
        VertexBufferKey key,
        List<RenderBatch> batches,
        GeometryBatchProcessor processor
    ) {
        // Custom batch processing logic
        return new GeometryBatchProcessor.ProcessorResult(resource, ranges);
    }

    @Override
    public String getStrategyId() {
        return "my_custom_strategy";
    }

    @Override
    public boolean appliesTo(VertexBufferKey key) {
        // Return true if this strategy should handle the key
        return key.components().size() > 3; // Example: more than 3 components
    }

    @Override
    public int getPriority() {
        return 5; // Lower number = higher priority
    }
}

// Register the strategy
BatchProcessingStrategyRegistry.getInstance().register(new MyCustomStrategy());
```

---

## Breaking Changes

### Removed Classes
The following classes have been removed:
- `ByteBufferFiller` - Use `VertexDataBuilder` with `MemoryBufferWriter` instead
- `DirectDataFiller` - Removed, use appropriate writer implementation
- `MemoryFiller` - Use `VertexDataBuilder` directly
- `WriteStrategy` interfaces - No longer needed with new architecture
- `FillerUsageExample` - Removed as example

### Deprecated Methods
- `VertexBufferKey.isInstanced()` → Use `hasInstancing()`
- `VertexBufferKey.instanceLayout()` → Use `components()` and inspect specs
- `VertexResource.BINDING_MAIN` → Use explicit binding point `0`
- `VertexResource.BINDING_INSTANCE` → Use explicit binding points `1, 2, 3, ...`
- `VertexResource.getStaticFormat()` → Use `getComponent(0).getFormat()`
- `VertexResource.getStaticVBO()` → Use `getComponent(0).getVboHandle()`
- `DynamicTypeMesh.fill(VertexFiller)` → Use `fill(VertexDataBuilder, Matrix4f)`
- `InstancedLayoutProvider.fillInstanceData(VertexDataBuilder)` → Override `fillInstanceData(int, VertexDataBuilder)` for multi-component

---

## Summary

The new architecture provides:
1. **Extensibility**: Strategy pattern allows custom batch processing
2. **Flexibility**: Component-based VBO design supports arbitrary configurations
3. **Performance**: Zero-copy BakedMesh integration
4. **Multi-Component**: Multiple instance formats at different binding points
5. **Simplified API**: Direct `VertexDataBuilder` usage instead of wrapper classes

For questions or issues, refer to the individual class documentation or open an issue on the project repository.
