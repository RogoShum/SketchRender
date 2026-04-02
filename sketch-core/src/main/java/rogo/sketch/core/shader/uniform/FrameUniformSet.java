package rogo.sketch.core.shader.uniform;

public record FrameUniformSet(UniformValueSnapshot legacySnapshot) {
    private static final FrameUniformSet EMPTY = new FrameUniformSet(UniformValueSnapshot.empty());

    public FrameUniformSet {
        legacySnapshot = legacySnapshot != null ? legacySnapshot : UniformValueSnapshot.empty();
    }

    public static FrameUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return legacySnapshot.isEmpty();
    }
}
