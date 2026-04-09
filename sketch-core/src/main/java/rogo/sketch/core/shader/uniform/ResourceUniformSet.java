package rogo.sketch.core.shader.uniform;

public record ResourceUniformSet(UniformValueSnapshot snapshot) {
    private static final ResourceUniformSet EMPTY = new ResourceUniformSet(UniformValueSnapshot.empty());

    public ResourceUniformSet {
        snapshot = snapshot != null ? snapshot : UniformValueSnapshot.empty();
    }

    public static ResourceUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return snapshot.isEmpty();
    }
}
