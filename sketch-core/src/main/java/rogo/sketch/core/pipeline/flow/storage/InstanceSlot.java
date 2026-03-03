package rogo.sketch.core.pipeline.flow.storage;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Indexed storage slot for a graphics instance.
 */
public class InstanceSlot<G extends Graphics, I extends InstanceInfo<G>> {
    private int index;
    private final G graphics;
    private I info;
    private RenderParameter renderParameter;
    private KeyId containerId;
    private Object batchKey;
    private RenderBatch<I> batch;

    public InstanceSlot(int index, G graphics) {
        this.index = index;
        this.graphics = graphics;
    }

    public int index() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public G graphics() {
        return graphics;
    }

    public I info() {
        return info;
    }

    public void setInfo(I info) {
        this.info = info;
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public void setRenderParameter(RenderParameter renderParameter) {
        this.renderParameter = renderParameter;
    }

    public KeyId containerId() {
        return containerId;
    }

    public void setContainerId(KeyId containerId) {
        this.containerId = containerId;
    }

    public Object batchKey() {
        return batchKey;
    }

    public void setBatchKey(Object batchKey) {
        this.batchKey = batchKey;
    }

    public RenderBatch<I> batch() {
        return batch;
    }

    public void setBatch(RenderBatch<I> batch) {
        this.batch = batch;
    }
}