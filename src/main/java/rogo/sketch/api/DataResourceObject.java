package rogo.sketch.api;

public interface DataResourceObject extends ResourceObject {
    long getDataCount();

    long getCapacity();

    long getStride();

    long getMemoryAddress();
}