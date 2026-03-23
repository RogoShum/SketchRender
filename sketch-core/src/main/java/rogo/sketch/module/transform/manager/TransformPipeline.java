package rogo.sketch.module.transform.manager;

import rogo.sketch.core.resource.buffer.ShaderStorageBuffer;

import java.util.List;

/**
 * Compatibility facade for render-owned transform GPU input resources.
 */
public class TransformPipeline {
    private final TransformGpuInputSet gpuInputSet;

    public TransformPipeline(int initialCapacity) {
        this.gpuInputSet = new TransformGpuInputSet(initialCapacity);
    }

    void loadSnapshot(TransformUploadSnapshot snapshot) {
        gpuInputSet.loadSnapshot(snapshot);
    }

    void applyFrameOverrides(List<TransformBinding> frameBindings, TransformUploadSnapshot baseSnapshot) {
        gpuInputSet.applyFrameOverrides(frameBindings, baseSnapshot);
    }

    public void upload() {
        gpuInputSet.upload();
    }

    public ShaderStorageBuffer indexSSBO() {
        return gpuInputSet.indexSSBO();
    }

    public ShaderStorageBuffer inputSSBO() {
        return gpuInputSet.inputSSBO();
    }

    public List<TransformDispatchRange> getDispatchRanges() {
        return gpuInputSet.dispatchRanges();
    }

    public int getMaxDepth() {
        return gpuInputSet.maxDepth();
    }

    public void cleanup() {
        gpuInputSet.cleanup();
    }
}