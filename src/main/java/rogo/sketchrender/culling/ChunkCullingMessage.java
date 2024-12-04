package rogo.sketchrender.culling;

public class ChunkCullingMessage {
    private int renderDistance = 0;
    private int spacePartitionSize = 0;
    public int queueUpdateCount = 0;
    public int lastQueueUpdateCount = 0;

    public void generateIndex(int renderDistance) {
        this.renderDistance = renderDistance;
        spacePartitionSize = 2 * renderDistance + 1;
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public int getSpacePartitionSize() {
        return spacePartitionSize;
    }
}
