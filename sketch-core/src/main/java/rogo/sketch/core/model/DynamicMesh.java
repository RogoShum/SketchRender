package rogo.sketch.core.model;

import rogo.sketch.core.api.model.DynamicTypeMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.util.KeyId;

import java.util.function.Consumer;

/**
 * Implementation of a dynamic mesh that generates data on demand via a consumer.
 */
public class DynamicMesh implements DynamicTypeMesh {
    private final StructLayout format;
    private final PrimitiveType primitiveType;
    private final int vertexCount;
    private final int indexCount;
    private final Consumer<VertexRecordWriter> generator;
    private final KeyId id;
    private int[] indices;

    public DynamicMesh(KeyId id, StructLayout format, PrimitiveType primitiveType, int vertexCount, int indexCount, Consumer<VertexRecordWriter> generator) {
        this.format = format;
        this.primitiveType = primitiveType;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.generator = generator;
        this.id = id;
    }

    public void setIndices(int[] indices) {
        this.indices = indices;
    }

    public int[] getIndices() {
        return indices;
    }

    @Override
    public KeyId getKetId() {
        return id;
    }

    @Override
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    @Override
    public StructLayout getVertexFormat() {
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

    @Override
    public int getVertexOffset() {
        return 0;
    }

    @Override
    public int getIndexOffset() {
        return 0;
    }

    public Consumer<VertexRecordWriter> generator() {
        return generator;
    }
}

