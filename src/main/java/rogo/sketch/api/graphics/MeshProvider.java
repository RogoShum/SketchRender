package rogo.sketch.api.graphics;

import rogo.sketch.render.model.MeshGroup;

//TODO: 等下这个类应该改为适配于mesh、modelMesh、meshGroup之类的...
public interface MeshProvider {
    MeshGroup getMesh();

    int getVertexCount();

    long getIndicesOffset();

    long getIndicesCount();
}