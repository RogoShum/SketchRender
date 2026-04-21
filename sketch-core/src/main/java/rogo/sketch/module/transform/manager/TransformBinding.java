package rogo.sketch.module.transform.manager;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsUpdateDomain;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.module.transform.TransformData;
import rogo.sketch.core.util.TripleBuffer;

/**
 * Internal transform-system binding for an ECS graphics entity.
 */
public final class TransformBinding {
    private final GraphicsEntityId entityId;
    private final int transformId;
    private final GraphicsUpdateDomain updateDomain;
    private final GraphicsBuiltinComponents.TransformBindingComponent bindingComponent;
    @Nullable
    private final GraphicsBuiltinComponents.TransformHierarchyComponent hierarchyComponent;
    private final TripleBuffer<TransformData> tickBuffers = new TripleBuffer<>(TransformData::new);
    private final TransformData frameData = new TransformData();
    private int parentTransformId = -1;
    private int depth = 0;

    public TransformBinding(
            GraphicsEntityId entityId,
            int transformId,
            GraphicsUpdateDomain updateDomain,
            GraphicsBuiltinComponents.TransformBindingComponent bindingComponent,
            @Nullable GraphicsBuiltinComponents.TransformHierarchyComponent hierarchyComponent) {
        this.entityId = entityId;
        this.transformId = transformId;
        this.updateDomain = updateDomain;
        this.bindingComponent = bindingComponent;
        this.hierarchyComponent = hierarchyComponent;
    }

    public GraphicsEntityId entityId() {
        return entityId;
    }

    public int transformId() {
        return transformId;
    }

    public GraphicsUpdateDomain updateDomain() {
        return updateDomain;
    }

    public GraphicsBuiltinComponents.TransformBindingComponent bindingComponent() {
        return bindingComponent;
    }

    @Nullable
    public GraphicsBuiltinComponents.TransformHierarchyComponent hierarchyComponent() {
        return hierarchyComponent;
    }

    public int parentTransformId() {
        return parentTransformId;
    }

    public void setParentTransformId(int parentTransformId) {
        this.parentTransformId = parentTransformId;
    }

    public int depth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public TransformData previousTickData() {
        return tickBuffers.getPrev();
    }

    public TransformData currentTickData() {
        return tickBuffers.getRead();
    }

    public TransformData pendingTickData() {
        return tickBuffers.getWrite();
    }

    public TransformData frameData() {
        return frameData;
    }

    public void swapTickBuffers() {
        tickBuffers.swap();
    }

    public void seedAllTickBuffers(TransformData data) {
        previousTickData().copyFrom(data);
        currentTickData().copyFrom(data);
        pendingTickData().copyFrom(data);
    }
}
