package rogo.sketch.render.command;

import org.lwjgl.opengl.GL45;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.vertex.VertexDataShard;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.UniformBatchGroup;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.resource.buffer.IndexBufferResource;
import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Render command for rasterization draw calls.
 * <p>
 * Encapsulates all parameters needed for
 * glDrawElementsInstancedBaseVertexBaseInstance
 * and similar draw calls. Supports instanced rendering with proper offset
 * calculations.
 * </p>
 */
public class DrawRenderCommand extends RenderCommand {
    private final VertexResource vertexResource;
    private final PrimitiveType primitiveType;
    private final VertexDataShard vertexDataShard;
    private final int instanceCount;
    private final int baseInstance;

    public DrawRenderCommand(
            VertexResource vertexResource,
            RenderSetting renderSetting,
            ResourceBinding resourceBinding,
            Identifier stageId,
            PrimitiveType primitiveType,
            VertexDataShard vertexDataShard,
            int instanceCount,
            int baseInstance) {
        this(vertexResource, renderSetting, resourceBinding, stageId, primitiveType,
                vertexDataShard, instanceCount, baseInstance, new ArrayList<>());
    }

    public DrawRenderCommand(
            VertexResource vertexResource,
            RenderSetting renderSetting,
            ResourceBinding resourceBinding,
            Identifier stageId,
            PrimitiveType primitiveType,
            VertexDataShard vertexDataShard,
            int instanceCount,
            int baseInstance,
            List<UniformBatchGroup> uniformBatches) {
        super(renderSetting, resourceBinding, stageId, uniformBatches);
        this.vertexResource = vertexResource;
        this.primitiveType = primitiveType;
        this.vertexDataShard = vertexDataShard;
        this.instanceCount = instanceCount;
        this.baseInstance = baseInstance;
    }

    @Override
    public void execute(RenderContext context) {
        if (!isValid()) {
            return;
        }

        vertexResource.bind();
        try {
            // Ensure index buffer is uploaded
            if (vertexResource.hasIndices()) {
                IndexBufferResource indexBuffer = vertexResource.getIndexBuffer();
                if (indexBuffer.isDirty()) {
                    indexBuffer.upload();
                }
            }

            // Use the unified GL call with all offsets
            // Note: indexOffset is in bytes for glDrawElements
            // baseVertex is usually int for DrawElementsBaseVertex
            // We use GL45.glDrawElementsInstancedBaseVertexBaseInstance if available,
            // otherwise fallback to GL31/GL42 features.

            // Assuming GL4.5 or ARB_shader_draw_parameters / ARB_base_instance support for full feature set.
            // For older GL versions, we might need multiple calls or reduced functionality.
            // Since this project uses DSA (GL4.5), we prefer that.

            // NOTE: indexOffset is expected to be in BYTES (pointer offset).

            GL45.glDrawElementsInstancedBaseVertexBaseInstance(
                    primitiveType.glType(),
                    vertexDataShard.indexCount(),
                    vertexResource.getIndexBuffer().currentIndexType().glType(),
                    vertexDataShard.indicesOffset(),
                    instanceCount,
                    (int) vertexDataShard.vertexOffset(),
                    baseInstance
            );
        } finally {
            vertexResource.unbind();
        }
    }

    @Override
    public void bindResources() {
        if (vertexResource != null) {
            vertexResource.bind();
        }
    }

    @Override
    public void unbindResources() {
        if (vertexResource != null) {
            vertexResource.unbind();
        }
    }

    @Override
    public String getCommandType() {
        return "Draw";
    }

    @Override
    public boolean isValid() {
        return vertexDataShard != null &&
                vertexDataShard.indexCount() > 0 &&
                instanceCount > 0 &&
                vertexResource != null;
    }

    // ===== Draw-Specific Getters =====

    public VertexResource getVertexResource() {
        return vertexResource;
    }

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    public VertexDataShard getVertexDataShard() {
        return vertexDataShard;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public int getBaseInstance() {
        return baseInstance;
    }

    @Override
    public String toString() {
        return "DrawRenderCommand{" +
                "stageId=" + stageId +
                ", primitiveType=" + primitiveType +
                ", indexCount=" + (vertexDataShard != null ? vertexDataShard.indexCount() : 0) +
                ", instanceCount=" + instanceCount +
                ", baseInstance=" + baseInstance +
                '}';
    }
}
