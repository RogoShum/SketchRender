package rogo.sketch.core.instance;

import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.transform.manager.TransformManager;
import rogo.sketch.module.transform.manager.TransformPipeline;

/**
 * TransformComputeGraphics - Compute Shader for Transform Matrix Calculation
 * <p>
 * Dispatches the transform_matrix compute shader to calculate world matrices
 * from transform input data. Uses tiered dispatch with barriers for hierarchical
 * transforms (parents must be computed before children).
 */
public class TransformComputeGraphics extends ComputeGraphics {
    private final ResourceReference<PartialRenderSetting> transformMatrixSetting;
    private final TransformManager transformManager;

    public TransformComputeGraphics(KeyId keyId, KeyId renderSetting, boolean sync, TransformManager transformManager) {
        super(keyId, null, (context, shader) -> {
            if (sync) {
                dispatchPipeline(shader, transformManager.getSyncPipeline());
            } else {
                dispatchPipeline(shader, transformManager.getAsyncPipeline());
            }
            shader.shaderStorageBarrier();
        });

        this.transformManager = transformManager;
        this.transformMatrixSetting = GraphicsResourceManager.getInstance().getReference(ResourceTypes.PARTIAL_RENDER_SETTING, renderSetting);
    }

    private static void dispatchPipeline(ComputeShader shader, TransformPipeline pipeline) {
        var ranges = pipeline.getDispatchRanges();
        if (ranges.isEmpty()) return;

        int maxDepth = pipeline.getMaxDepth();
        UniformHookGroup uniformHookGroup = shader.getUniformHookGroup();

        for (int d = 0; d <= maxDepth; d++) {
            if (d >= ranges.size()) break;
            var range = ranges.get(d);

            if (range.count() > 0) {
                uniformHookGroup.getUniform("u_batchOffset").set(range.offset());
                uniformHookGroup.getUniform("u_batchCount").set(range.count());

                int groups = (range.count() + 63) / 64;
                shader.dispatch(groups, 1, 1);

                // Barrier for next layer
                if (d < maxDepth) {
                    shader.shaderStorageBarrier();
                }
            }
        }
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        if (transformMatrixSetting.isAvailable()) {
            return transformMatrixSetting.get();
        }
        return null;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return transformManager.getActiveCount() > 0;
    }

    /**
     * Get the associated MatrixManager.
     */
    public TransformManager getMatrixManager() {
        return transformManager;
    }
}