package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.BatchKey;
import rogo.sketch.core.pipeline.flow.MeshRenderBatch;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.information.RasterizationInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Rasterization adapter over merged batch container core.
 */
public class RasterizationBatchContainer 
        extends AbstractMergedBatchContainer<MeshBasedGraphics, RasterizationInstanceInfo, BatchKey> {

    public RasterizationBatchContainer() {
        registerContainerDescriptor(DefaultBatchContainers.QUEUE_DESCRIPTOR);
        registerContainerDescriptor(DefaultBatchContainers.AABB_TREE_DESCRIPTOR);
        registerContainerDescriptor(DefaultBatchContainers.OCTREE_DESCRIPTOR);
        registerContainerDescriptor(DefaultBatchContainers.PRIORITY_DESCRIPTOR);
    }

    @Override
    protected BatchKey computeBatchKey(
            MeshBasedGraphics graphics,
            RenderParameter renderParameter,
            RenderSetting renderSetting) {
        PreparedMesh mesh = graphics.getPreparedMesh();
        MeshHolderPool.MeshHolder meshHolder = MeshHolderPool.getInstance().get(mesh);
        return new BatchKey(renderSetting, meshHolder);
    }

    @Override
    protected RenderBatch<RasterizationInstanceInfo> createRenderBatch(
            BatchKey batchKey,
            RenderSetting renderSetting,
            MeshBasedGraphics graphics,
            RenderParameter renderParameter) {
        PreparedMesh mesh = graphics.getPreparedMesh();
        MeshHolderPool.MeshHolder meshHolder = MeshHolderPool.getInstance().get(mesh);
        if (meshHolder != null && meshHolder.bakedTypeMesh() != null) {
            return new MeshRenderBatch(renderSetting, meshHolder.bakedTypeMesh());
        }
        return new RenderBatch<>(renderSetting);
    }

    @Override
    protected RasterizationInstanceInfo createInstanceInfo(
            MeshBasedGraphics graphics,
            RenderSetting renderSetting,
            RenderParameter renderParameter) {
        PreparedMesh mesh = graphics.getPreparedMesh();
        int vertexCount = mesh != null ? mesh.getVertexCount() : 0;
        return new RasterizationInstanceInfo(graphics, renderSetting, mesh, vertexCount);
    }

    @Override
    public Class<MeshBasedGraphics> getGraphicsType() {
        return MeshBasedGraphics.class;
    }
    
    @Override
    public Class<RasterizationInstanceInfo> getInfoType() {
        return RasterizationInstanceInfo.class;
    }
}
