package rogo.sketchrender.api;

public interface DataBufferObject extends BufferObject {
    long getDataCount();

    long getCapacity();

    long getStride();

    long getMemoryAddress();
}