package rogo.sketchrender.api;

import rogo.sketchrender.shader.uniform.SSBO;

public interface SectionData {
    void setMeshData(SSBO meshData, int pass);

    void bindMeshData(int slot);
    void bindCounter(int slot);
    void bindIndirectCommand(int slot);
    void bindCommandBuffer();
    void bindCounterBuffer();

    void clearCounter();
}
