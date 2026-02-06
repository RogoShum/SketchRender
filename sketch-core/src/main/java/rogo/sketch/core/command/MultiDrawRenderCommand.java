package rogo.sketch.core.command;

import org.lwjgl.opengl.GL43;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.vertex.VertexDataShard;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.information.RasterizationInstanceInfo;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.util.KeyId;

/**
 * A render command that executes a Multi-Draw Indirect call.
 * Extends DrawRenderCommand for rasterization operations.
 */
public class MultiDrawRenderCommand extends DrawRenderCommand {
    private final IndirectCommandBuffer indirectBuffer;
    private final int drawCount;
    private final long indirectOffset;
    private final int indirectStride;

    public MultiDrawRenderCommand(
            VertexResource vertexResource,
            IndirectCommandBuffer indirectBuffer,
            RenderSetting renderSetting,
            KeyId stageId,
            int drawCount,
            long indirectOffset,
            RenderBatch<? extends RasterizationInstanceInfo> batch) {
        super(
                vertexResource,
                renderSetting,
                renderSetting.resourceBinding(),
                stageId,
                renderSetting.renderParameter().primitiveType(),
                new VertexDataShard(0, 0, 0, 0),
                0,
                0,
                batch.getUniformBatches());
        this.indirectBuffer = indirectBuffer;
        this.drawCount = drawCount;
        this.indirectOffset = indirectOffset;
        this.indirectStride = (int) indirectBuffer.getStride();
    }

    public MultiDrawRenderCommand(
            VertexResource vertexResource,
            IndirectCommandBuffer indirectBuffer,
            RenderSetting renderSetting,
            KeyId stageId,
            int drawCount,
            RenderBatch<? extends RasterizationInstanceInfo> batch) {
        this(vertexResource, indirectBuffer, renderSetting, stageId, drawCount, 0, batch);
    }

    @Override
    public void execute(RenderContext context) {
        if (drawCount == 0)
            return;

        PrimitiveType primitiveType = getPrimitiveType();
        VertexResource resource = getVertexResource();
        if (resource == null)
            return;

        resource.bind();
        indirectBuffer.bind();

        if (primitiveType.requiresIndexBuffer()) {
            if (resource.getIndexBuffer().isDirty()) {
                resource.getIndexBuffer().upload();
                resource.getIndexBuffer().bind();
            }

            GL43.glMultiDrawElementsIndirect(
                    primitiveType.glType(),
                    resource.getIndexBuffer().currentIndexType().glType(),
                    indirectOffset,
                    drawCount,
                    indirectStride);
        } else {
            GL43.glMultiDrawArraysIndirect(
                    primitiveType.glType(),
                    indirectOffset,
                    drawCount,
                    indirectStride);
        }

        resource.unbind();
    }

    @Override
    public String getCommandType() {
        return "MultiDraw";
    }

    @Override
    public boolean isValid() {
        return drawCount > 0 && indirectBuffer != null && getVertexResource() != null;
    }

    public IndirectCommandBuffer getIndirectBuffer() {
        return indirectBuffer;
    }

    public int getDrawCount() {
        return drawCount;
    }

    public long getIndirectOffset() {
        return indirectOffset;
    }

    public int getIndirectStride() {
        return indirectStride;
    }

    @Override
    public String toString() {
        return "MultiDrawRenderCommand{" +
                "drawCount=" + drawCount +
                ", offset=" + indirectOffset +
                ", primitiveType=" + getPrimitiveType() +
                '}';
    }
}