package rogo.sketch.core.pipeline.flow.v2;

/**
 * Stable dense-handle identifier for V2 instance registries.
 */
public record InstanceHandle(int slot, int generation) {
    public static final InstanceHandle INVALID = new InstanceHandle(-1, -1);

    public boolean isValid() {
        return slot >= 0 && generation >= 0;
    }
}
