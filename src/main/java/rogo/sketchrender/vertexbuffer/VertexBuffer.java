package rogo.sketchrender.vertexbuffer;

import rogo.sketchrender.api.ResourceBufferObject;

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