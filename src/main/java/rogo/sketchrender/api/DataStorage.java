package rogo.sketchrender.api;

public interface DataStorage {
    void bindMeshData(int slot);
    void bindCounter(int slot);
    void bindIndirectCommand(int slot);
    void bindCommandBuffer();
    void bindCounterBuffer();

    void clearCounter();
}
