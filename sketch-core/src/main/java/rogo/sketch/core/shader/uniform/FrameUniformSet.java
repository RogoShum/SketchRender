package rogo.sketch.core.shader.uniform;

public record FrameUniformSet(UniformValueSnapshot snapshot) {
    private static final FrameUniformSet EMPTY = new FrameUniformSet(UniformValueSnapshot.empty());

    public FrameUniformSet {
        snapshot = snapshot != null ? snapshot : UniformValueSnapshot.empty();
    }

    public static FrameUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return snapshot.isEmpty();
    }
}
