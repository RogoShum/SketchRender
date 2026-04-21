package rogo.sketch.core.backend;

public record ResourceEpoch(long value) {
    public static final ResourceEpoch ZERO = new ResourceEpoch(0L);

    public ResourceEpoch next() {
        return new ResourceEpoch(value + 1L);
    }
}
