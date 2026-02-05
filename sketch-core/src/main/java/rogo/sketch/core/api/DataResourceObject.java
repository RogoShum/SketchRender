package rogo.sketch.core.api;

/**
 * Interface for data buffer resources that hold structured data.
 * Extends GpuObject since data buffers have native handles.
 */
public interface DataResourceObject extends GpuObject {
    long getDataCount();

    long getCapacity();

    long getStride();

    long getMemoryAddress();
}