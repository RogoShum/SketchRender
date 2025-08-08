package rogo.sketch.render.vertexbuffer;

import rogo.sketch.api.ResourceBufferObject;

public class VertexBuffer implements ResourceBufferObject {
    private int id;

    @Override
    public int getHandle() {
        return id;
    }

    @Override
    public void bind() {

    }

    @Override
    public void unbind() {

    }

    @Override
    public void dispose() {

    }
}