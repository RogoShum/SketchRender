package rogo.sketch.core.pipeline.indirect;

/**
 * Persistent slice allocation inside one indirect-command lane.
 */
public record PersistentIndirectSlice(
        int startCommandIndex,
        int commandCapacity,
        int commandCount
) {
    public int endCommandIndex() {
        return startCommandIndex + commandCapacity;
    }
}
