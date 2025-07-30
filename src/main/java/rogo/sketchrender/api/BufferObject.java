package rogo.sketchrender.api;

public interface BufferObject {
    int getId();

    long getDataCount();

    long getCapacity();

    long getStride();

    long getMemoryAddress();

    void discard();
}