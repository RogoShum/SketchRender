package rogo.sketch.render.model;

import org.joml.Matrix4f;
import rogo.sketch.api.model.DynamicTypeMesh;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.render.data.format.DataFormat;

import java.util.function.Consumer;

/**
 * Implementation of a dynamic mesh that generates data on demand via a consumer.
 */
public class DynamicMesh implements DynamicTypeMesh {
    private final DataFormat format;
    private final PrimitiveType primitiveType;
    private final int vertexCount;
    private final int indexCount;
    private final Consumer<VertexDataBuilder> generator;
    private int[] indices;

    public DynamicMesh(DataFormat format, PrimitiveType primitiveType, int vertexCount, int indexCount, Consumer<VertexDataBuilder> generator) {
        this.format = format;
        this.primitiveType = primitiveType;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.generator = generator;
    }

    public void setIndices(int[] indices) {
        this.indices = indices;
    }

    public int[] getIndices() {
        return indices;
    }

    @Override
    public void fill(int bindingPoint, VertexDataBuilder builder, Matrix4f transform) {
        if (generator != null) {
            generator.accept(builder);
        }
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
}