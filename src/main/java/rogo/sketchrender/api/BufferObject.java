package rogo.sketchrender.api;

public interface BufferObject {

    int getId();

    long getDataNum();

    long getSize();

    long getStride();

    long getMemoryAddress();
}
