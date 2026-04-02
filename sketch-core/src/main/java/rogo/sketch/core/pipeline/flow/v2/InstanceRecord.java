package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

public final class InstanceRecord<G extends Graphics> {
    private final InstanceHandle handle;
    private final G graphics;
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final RenderParameter renderParameter;
    private KeyId containerType;
    private SubmissionCapability submissionCapability;
    private long descriptorVersion;
    private long geometryVersion;
    private long boundsVersion;
    private CompiledRenderSetting compiledRenderSetting;
    private GeometryTraitsRef geometryTraitsRef;
    private VisibilityMetadata visibilityMetadata;
    private KeyId visibilityContainerHandle;
    private GeometryBatchKey geometryBucketHandle;
    private boolean discarded;
    private int dirtyMask = InstanceDirtyMask.DESCRIPTOR | InstanceDirtyMask.GEOMETRY | InstanceDirtyMask.BOUNDS;

    public InstanceRecord(
            InstanceHandle handle,
            G graphics,
            KeyId stageId,
            PipelineType pipelineType,
            RenderParameter renderParameter,
            KeyId containerType) {
        this.handle = handle;
        this.graphics = graphics;
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.renderParameter = renderParameter;
        this.containerType = containerType;
        this.submissionCapability = graphics != null ? graphics.submissionCapability() : SubmissionCapability.DIRECT_BATCHABLE;
        this.visibilityContainerHandle = containerType;
    }

    public InstanceHandle handle() {
        return handle;
    }

    public G graphics() {
        return graphics;
    }

    public KeyId stageId() {
        return stageId;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public KeyId containerType() {
        return containerType;
    }

    public void setContainerType(KeyId containerType) {
        this.containerType = containerType;
        this.visibilityContainerHandle = containerType;
        markDirty(InstanceDirtyMask.MEMBERSHIP);
    }

    public SubmissionCapability submissionCapability() {
        return submissionCapability;
    }

    public void setSubmissionCapability(SubmissionCapability submissionCapability) {
        this.submissionCapability = submissionCapability != null
                ? submissionCapability
                : SubmissionCapability.DIRECT_BATCHABLE;
    }

    public long descriptorVersion() {
        return descriptorVersion;
    }

    public void setDescriptorVersion(long descriptorVersion) {
        this.descriptorVersion = descriptorVersion;
    }

    public long geometryVersion() {
        return geometryVersion;
    }

    public void setGeometryVersion(long geometryVersion) {
        this.geometryVersion = geometryVersion;
    }

    public long boundsVersion() {
        return boundsVersion;
    }

    public void setBoundsVersion(long boundsVersion) {
        this.boundsVersion = boundsVersion;
    }

    public CompiledRenderSetting compiledRenderSetting() {
        return compiledRenderSetting;
    }

    public void setCompiledRenderSetting(CompiledRenderSetting compiledRenderSetting) {
        this.compiledRenderSetting = compiledRenderSetting;
    }

    public GeometryTraitsRef geometryTraitsRef() {
        return geometryTraitsRef;
    }

    public void setGeometryTraitsRef(GeometryTraitsRef geometryTraitsRef) {
        this.geometryTraitsRef = geometryTraitsRef;
        this.geometryBucketHandle = geometryTraitsRef != null ? geometryTraitsRef.geometryBatchKey() : null;
    }

    public VisibilityMetadata visibilityMetadata() {
        return visibilityMetadata;
    }

    public void setVisibilityMetadata(VisibilityMetadata visibilityMetadata) {
        this.visibilityMetadata = visibilityMetadata;
    }

    public KeyId visibilityContainerHandle() {
        return visibilityContainerHandle;
    }

    public void setVisibilityContainerHandle(KeyId visibilityContainerHandle) {
        this.visibilityContainerHandle = visibilityContainerHandle;
    }

    public GeometryBatchKey geometryBucketHandle() {
        return geometryBucketHandle;
    }

    public boolean discarded() {
        return discarded;
    }

    public void markDiscarded() {
        this.discarded = true;
        markDirty(InstanceDirtyMask.DISCARDED);
    }

    public int dirtyMask() {
        return dirtyMask;
    }

    public void setDirtyMask(int dirtyMask) {
        this.dirtyMask = dirtyMask;
    }

    public void markDirty(int flag) {
        this.dirtyMask = InstanceDirtyMask.add(this.dirtyMask, flag);
    }

    public void clearDirty(int flag) {
        this.dirtyMask = InstanceDirtyMask.remove(this.dirtyMask, flag);
    }

    public void clearAllDirty() {
        this.dirtyMask = InstanceDirtyMask.NONE;
    }
}
