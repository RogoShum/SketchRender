package rogo.sketch.render.data.vertex;

public interface Mesh {
    int getVertexCount();

    int getIndicesCount();

    void pushMeshVertex(long pointer, long vertexOffset, long indicesOffset);

    boolean isBaked();
}