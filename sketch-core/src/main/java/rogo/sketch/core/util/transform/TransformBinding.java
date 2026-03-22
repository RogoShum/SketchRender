package rogo.sketch.core.util.transform;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.TransformParentSource;
import rogo.sketch.core.transform.TransformData;
import rogo.sketch.core.util.TripleBuffer;

/**
 * Internal transform-system binding for a graphics instance.
 */
public final class TransformBinding {
    private final Graphics graphics;
    private final int transformId;
    private final TransformUpdateDomain updateDomain;
    @Nullable
    private final TransformParentSource parentSource;
    private final TripleBuffer<TransformData> tickBuffers = new TripleBuffer<>(TransformData::new);
    private int parentTransformId = -1;
    private int depth = 0;

    public TransformBinding(Graphics graphics, int transformId, TransformUpdateDomain updateDomain,
                            @Nullable TransformParentSource parentSource) {
        this.graphics = graphics;
        this.transformId = transformId;
        this.updateDomain = updateDomain;
        this.parentSource = parentSource;
    }

    public Graphics graphics() {
        return graphics;
    }

    public int transformId() {
        return transformId;
    }

    public TransformUpdateDomain updateDomain() {
        return updateDomain;
    }

    @Nullable
    public TransformParentSource parentSource() {
        return parentSource;
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

    public void swapTickBuffers() {
        tickBuffers.swap();
    }

    public void seedAllTickBuffers(TransformData data) {
        previousTickData().copyFrom(data);
        currentTickData().copyFrom(data);
        pendingTickData().copyFrom(data);
    }
}
