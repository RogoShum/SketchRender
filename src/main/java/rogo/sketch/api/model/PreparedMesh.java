package rogo.sketch.api.model;

public sealed interface PreparedMesh permits BakedTypeMesh, DynamicTypeMesh {
    int getVertexCount();

    int getIndicesCount();
}