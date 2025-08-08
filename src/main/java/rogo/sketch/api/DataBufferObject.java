package rogo.sketch.api;

public interface DataBufferObject extends ResourceObject {
    long getDataCount();

    long getCapacity();

    long getStride();

    long getMemoryAddress();
}