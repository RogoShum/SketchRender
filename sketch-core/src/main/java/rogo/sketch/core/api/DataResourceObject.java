package rogo.sketch.core.api;

public interface DataResourceObject extends ResourceObject {
    long getDataCount();

    long getCapacity();

    long getStride();

    long getMemoryAddress();
}