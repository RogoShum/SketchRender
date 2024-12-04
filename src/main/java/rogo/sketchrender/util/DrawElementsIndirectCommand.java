package rogo.sketchrender.util;

public class DrawElementsIndirectCommand {
    public static final int SIZE = 20;
    public int count;
    public int instanceCount;
    public int firstIndex;
    public int baseVertex;
    public int baseInstance;

    public DrawElementsIndirectCommand(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        this.count = count;
        this.instanceCount = instanceCount;
        this.firstIndex = firstIndex;
        this.baseVertex = baseVertex;
        this.baseInstance = baseInstance;
    }

    public DrawElementsIndirectCommand() {
        this(0, 0, 0, 0, 0);
    }

    public DrawElementsIndirectCommand clone() {
        return new DrawElementsIndirectCommand(count, instanceCount, firstIndex, baseVertex, baseInstance);
    }

    @Override
    public String toString() {
        return String.format("DrawElementsIndirectCommand[count=%d, instanceCount=%d, firstIndex=%d, baseVertex=%d, baseInstance=%d]",
                count, instanceCount, firstIndex, baseVertex, baseInstance);
    }
}