package rogo.sketch.render.model;

import rogo.sketch.api.model.BakedTypeMesh;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
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