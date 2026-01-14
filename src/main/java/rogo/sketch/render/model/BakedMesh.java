package rogo.sketch.render.model;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import rogo.sketch.api.model.BakedTypeMesh;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.resource.buffer.IndexBufferResource;
import rogo.sketch.render.resource.buffer.VertexResource;

/**
 * Implementation of a static mesh baked into a VertexResource.
 * Uses GL_COPY_READ_BUFFER / GL_COPY_WRITE_BUFFER for efficient transfer.
 */
public class BakedMesh implements BakedTypeMesh {
    private final VertexResource sourceResource;
    private final DataFormat format;
    private final PrimitiveType primitiveType;

    // Source offsets and counts
    private final int srcVertexOffset; // in vertices
    private final int srcIndexOffset;  // in indices
    private final int vertexCount;
    private final int indexCount;

    public BakedMesh(VertexResource sourceResource, int srcVertexOffset, int srcIndexOffset, int vertexCount, int indexCount) {
        this.sourceResource = sourceResource;
        this.srcVertexOffset = srcVertexOffset;
        this.srcIndexOffset = srcIndexOffset;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;

        this.format = sourceResource.getStaticFormat();
        this.primitiveType = sourceResource.getPrimitiveType();
    }

    @Override
    public void copyTo(VertexResource target, int targetVertexOffset, int targetIndexOffset) {
        if (vertexCount == 0 || format == null) return;

        // 1. Copy Vertex Data
        int vertexStride = format.getStride();
        long sizeBytes = (long) vertexCount * vertexStride;
        long srcOffsetBytes = (long) srcVertexOffset * vertexStride;
        long dstOffsetBytes = (long) targetVertexOffset * vertexStride;

        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, sourceResource.getStaticVBO());
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, target.getStaticVBO());
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, srcOffsetBytes, dstOffsetBytes, sizeBytes);

        // 2. Copy Index Data (if exists)
        if (indexCount > 0 && isIndexed() && target.getIndexBuffer() != null) {
            IndexBufferResource srcIBO = sourceResource.getIndexBuffer();
            IndexBufferResource dstIBO = target.getIndexBuffer();

            if (srcIBO != null && dstIBO != null) {
                int indexStride = 4; // Assuming 32-bit indices (GL_UNSIGNED_INT)
                long indexSizeBytes = (long) indexCount * indexStride;
                long srcIndexOffsetBytes = (long) srcIndexOffset * indexStride;
                long dstIndexOffsetBytes = (long) targetIndexOffset * indexStride;

                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, srcIBO.getHandle());
                GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dstIBO.getHandle());
                GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, srcIndexOffsetBytes, dstIndexOffsetBytes, indexSizeBytes);
            }
        }

        // Restore state
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    @Override
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    @Override
    public DataFormat getVertexFormat() {
        return format;
    }

    @Override
    public int getVertexCount() {
        return vertexCount;
    }

    @Override
    public int getIndicesCount() {
        return indexCount;
    }

    public VertexResource getSourceResource() {
        return sourceResource;
    }

    @Override
    public int getVAOHandle() {
        // Get VBO handle at binding 0 from source resource
        var component = sourceResource.getComponent(0);
        return component != null ? component.getVboHandle() : 0;
    }

    @Override
    public int getSourceVertexOffset() {
        return srcVertexOffset;
    }

    @Override
    public int getSourceIndexOffset() {
        return srcIndexOffset;
    }
}