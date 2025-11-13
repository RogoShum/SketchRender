package rogo.sketch.api.model;

public non-sealed interface BakedTypeMesh extends PreparedMesh {
    void copyMeshVertex(long pointer, long vertexOffset, long indicesOffset);
}
