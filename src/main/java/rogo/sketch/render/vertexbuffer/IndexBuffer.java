package rogo.sketch.render.vertexbuffer;

import rogo.sketch.api.ResourceBufferObject;

public class IndexBuffer implements ResourceBufferObject {
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