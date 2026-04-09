package rogo.sketch.core.backend;

/**
 * Backend-owned indirect command buffer contract.
 */
public interface BackendIndirectBuffer extends BackendInstalledBuffer {
    long COMMAND_STRIDE_BYTES = 20L;

    long strideBytes();

    int commandCount();

    long writePositionBytes();

    long memoryAddress();

    void clear();

    void bind();

    void unbind();

    void upload();

    void addDrawArraysCommand(int count, int instanceCount, int first, int baseInstance);

    void addDrawElementsCommand(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance);
}

