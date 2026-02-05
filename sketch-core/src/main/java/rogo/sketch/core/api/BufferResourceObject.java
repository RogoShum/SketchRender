package rogo.sketch.core.api;

/**
 * Interface for GPU buffer resources (VBO, SSBO, UBO, etc.)
 * Extends GpuObject since buffers have native handles.
 */
public interface BufferResourceObject extends GpuObject {
    void bind();

    void unbind();
}