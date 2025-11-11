# Rendering System Migration Guide

## Overview

This guide explains the changes made to enable the legacy rendering mode and prepare for the new meshGroup-based three-stage pipeline.

## Changes Made

### 1. Legacy Mode Enabled (Default)

- **Legacy rendering mode is now the default** (`useNewPipeline = false`)
- All graphics instances are handled uniformly through `GraphicsInstance`
- Backward compatibility maintained for existing `SharedVertexProvider` and `IndependentVertexProvider`

### 2. New Provider Interfaces

Added missing interfaces for the new pipeline:

#### `RenderSettingProvider`
```java
public interface RenderSettingProvider extends GraphicsInstance {
    <C extends RenderContext> RenderSetting getRenderSetting(C context);
}
```

#### `VertexCountProvider`
```java
public interface VertexCountProvider extends GraphicsInstance {
    int getVertexCount();
}
```

#### `MeshGraphicsInstance` (Unified Interface)
```java
public interface MeshGraphicsInstance extends GraphicsInstance, 
        ModelMeshProvider, RenderSettingProvider {
    // Combines all functionality for both pipelines
}
```

### 3. GraphicsPass Simplification

- **Legacy Mode**: All graphics instances stored in unified `allGraphics` map
- **Backward Compatibility**: Old maps (`sharedGraphics`, `independentGraphics`, `customGraphics`) still populated
- Single tick loop for all instances
- Unified cleanup process

### 4. Mesh System Components

The new three-stage pipeline components are ready:

#### Stage 1: Data Collection
- `InfoCollector` - Extracts render information from graphics instances
- `GraphicsInformation` - Contains all rendering data for a single instance

#### Stage 2: Buffer Organization 
- `RenderList` - Organizes instances into batches with vertex offsets
- `RenderBatch` - Groups compatible instances for efficient rendering

#### Stage 3: Command Execution
- `RenderCommand` - Batched rendering commands
- `RenderCommandQueue` - Manages execution order by stage

## Usage Patterns

### For Legacy Mode (Current Default)

```java
// Create a graphics instance
public class MyGraphics implements GraphicsInstance {
    // Implement GraphicsInstance methods
}

// Add to pipeline
pipeline.addGraphInstance(stageId, new MyGraphics(id), renderSetting);
```

### For New Pipeline (Future)

```java
// Create a meshGroup-based graphics instance
public class MyMeshGraphics implements MeshGraphicsInstance {
    @Override
    public ModelMesh getModelMesh() {
        return myModelMesh;
    }
    
    @Override
    public <C extends RenderContext> RenderSetting getRenderSetting(C context) {
        return myRenderSetting;
    }
    
    // Other GraphicsInstance methods
}

// Enable new pipeline
pipeline.setUseNewPipeline(true);
```

## Migration Path

1. **Immediate (Legacy Mode)**: No changes needed for existing code
2. **Phase 1**: Gradually implement `MeshGraphicsInstance` for new graphics
3. **Phase 2**: Convert existing graphics to use meshGroup system
4. **Phase 3**: Enable new pipeline (`setUseNewPipeline(true)`)
5. **Phase 4**: Remove deprecated vertex provider interfaces

## New Pipeline Benefits

When ready to migrate to the new pipeline:

- **Better Performance**: Multi-draw batching with `baseVertex` offsets
- **Instanced Rendering**: Built-in support for instanced vertex attributes
- **Mesh-Based**: Native support for meshGroup data and vertex offsets
- **Async Processing**: Parallel vertex buffer filling for large batches

## Key Files Changed

- `GraphicsPipeline.java` - Added pipeline toggle, legacy mode default
- `GraphicsPass.java` - Unified instance management
- `api/graphics/` - New provider interfaces
- `render/information/` - New pipeline data structures
- `render/command/` - Render command system

## Testing

All existing functionality should work unchanged in legacy mode. Test with:

```java
// Verify legacy mode is active
assert !pipeline.isUsingNewPipeline();

// Test existing graphics instances still work
pipeline.addGraphInstance(stageId, existingGraphics, setting);
pipeline.renderAllStages();
```
